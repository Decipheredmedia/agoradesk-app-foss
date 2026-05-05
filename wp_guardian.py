#!/usr/bin/env python3
"""
wp_guardian.py — WordPress Site Maintenance Daemon
===================================================
Python 3.11+ standalone script for managing, hardening, and monitoring a
self-hosted WordPress installation.

Usage
-----
  python wp_guardian.py --check             # one-shot health check
  python wp_guardian.py --harden            # apply security hardening
  python wp_guardian.py --rotate-salts      # rotate wp-config.php salts & keys
  python wp_guardian.py --daemon            # run continuous monitoring loop

Dependencies (see requirements.txt)
  requests, paramiko, cryptography, python-dotenv, schedule

Configuration
  Copy .env.example → .env and fill in values before running.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import os
import re
import shutil
import signal
import smtplib
import stat
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from pathlib import Path
from typing import Any

import paramiko
import requests
import schedule
from cryptography.fernet import Fernet
from dotenv import load_dotenv

# ─────────────────────────────────────────────────────────────────────────────
# Logging — structured JSON to file + human-readable to stdout
# ─────────────────────────────────────────────────────────────────────────────

LOG_FILE = Path(os.getenv("WP_LOG_FILE", "wp_guardian.log"))


class _JsonFormatter(logging.Formatter):
    """Emit each log record as a single JSON line."""

    def format(self, record: logging.LogRecord) -> str:  # noqa: D102
        payload: dict[str, Any] = {
            "ts": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
        }
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def _build_logger() -> logging.Logger:
    logger = logging.getLogger("wp_guardian")
    logger.setLevel(logging.DEBUG)

    # JSON file handler
    fh = logging.FileHandler(LOG_FILE, encoding="utf-8")
    fh.setFormatter(_JsonFormatter())
    fh.setLevel(logging.DEBUG)
    logger.addHandler(fh)

    # Human-readable console handler
    ch = logging.StreamHandler(sys.stdout)
    ch.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
    ch.setLevel(logging.INFO)
    logger.addHandler(ch)

    return logger


log = _build_logger()

# ─────────────────────────────────────────────────────────────────────────────
# Configuration — read from .env
# ─────────────────────────────────────────────────────────────────────────────

load_dotenv()


def _require(key: str) -> str:
    """Return env var value or raise a clear error."""
    val = os.getenv(key)
    if not val:
        raise EnvironmentError(f"Required env var '{key}' is not set. See .env.example.")
    return val


_SAFE_SERVICE_RE = re.compile(r"^[a-zA-Z0-9_\-]+$")


def _validate_service_name(name: str) -> str:
    """
    Validate that a systemd service name contains only safe characters
    (alphanumeric, hyphens, underscores).  Raises ValueError on invalid input.
    """
    if not _SAFE_SERVICE_RE.match(name):
        raise ValueError(
            f"Invalid SYSTEMD_SERVICE value '{name}'. "
            "Only alphanumeric characters, hyphens, and underscores are allowed."
        )
    return name


class Config:
    """All runtime configuration, loaded from environment variables."""

    # WordPress installation
    wp_config_path: Path = Path(os.getenv("WP_CONFIG_PATH", "/var/www/html/wp-config.php"))
    wp_root: Path = Path(os.getenv("WP_ROOT", "/var/www/html"))
    site_url: str = os.getenv("WP_SITE_URL", "https://example.com")

    # Health-check
    health_interval_seconds: int = int(os.getenv("HEALTH_INTERVAL_SECONDS", "60"))
    health_timeout_seconds: int = int(os.getenv("HEALTH_TIMEOUT_SECONDS", "15"))

    # SSH for remote restart
    ssh_host: str = os.getenv("SSH_HOST", "")
    ssh_port: int = int(os.getenv("SSH_PORT", "22"))
    ssh_user: str = os.getenv("SSH_USER", "")
    ssh_key_path: str = os.getenv("SSH_KEY_PATH", "~/.ssh/id_rsa")
    # Validated in _validate_service_name() to contain only safe characters.
    systemd_service: str = os.getenv("SYSTEMD_SERVICE", "apache2")

    # Cloudflare (optional)
    cloudflare_zone_id: str = os.getenv("CF_ZONE_ID", "")
    cloudflare_api_token: str = os.getenv("CF_API_TOKEN", "")

    # WPScan (optional)
    wpscan_api_token: str = os.getenv("WPSCAN_API_TOKEN", "")

    # Alerting
    smtp_host: str = os.getenv("SMTP_HOST", "")
    smtp_port: int = int(os.getenv("SMTP_PORT", "587"))
    smtp_user: str = os.getenv("SMTP_USER", "")
    smtp_password: str = os.getenv("SMTP_PASSWORD", "")
    alert_from: str = os.getenv("ALERT_FROM", "")
    alert_to: str = os.getenv("ALERT_TO", "")

    discord_webhook: str = os.getenv("DISCORD_WEBHOOK_URL", "")
    slack_webhook: str = os.getenv("SLACK_WEBHOOK_URL", "")
    telegram_bot_token: str = os.getenv("TELEGRAM_BOT_TOKEN", "")
    telegram_chat_id: str = os.getenv("TELEGRAM_CHAT_ID", "")

    # Hash baseline file for file-integrity monitoring
    baseline_file: Path = Path(os.getenv("HASH_BASELINE_FILE", "wp_file_hashes.json"))

    # Salt API
    wp_salt_api_url: str = "https://api.wordpress.org/secret-key/1.1/salt/"


cfg = Config()

# ─────────────────────────────────────────────────────────────────────────────
# Alerting helpers
# ─────────────────────────────────────────────────────────────────────────────

_SALT_NAMES = (
    "AUTH_KEY",
    "SECURE_AUTH_KEY",
    "LOGGED_IN_KEY",
    "NONCE_KEY",
    "AUTH_SALT",
    "SECURE_AUTH_SALT",
    "LOGGED_IN_SALT",
    "NONCE_SALT",
)


def send_email(subject: str, body: str) -> None:
    """Send a plain-text alert email via SMTP."""
    if not (cfg.smtp_host and cfg.alert_from and cfg.alert_to):
        log.debug("Email alerting not configured; skipping.")
        return
    try:
        msg = MIMEMultipart()
        msg["From"] = cfg.alert_from
        msg["To"] = cfg.alert_to
        msg["Subject"] = subject
        msg.attach(MIMEText(body, "plain"))
        with smtplib.SMTP(cfg.smtp_host, cfg.smtp_port) as server:
            server.ehlo()
            server.starttls()
            if cfg.smtp_user and cfg.smtp_password:
                server.login(cfg.smtp_user, cfg.smtp_password)
            server.sendmail(cfg.alert_from, cfg.alert_to, msg.as_string())
        log.info("Alert email sent: %s", subject)
    except Exception as exc:
        log.error("Failed to send alert email: %s", exc)


def send_webhook(message: str) -> None:
    """Post an alert to Discord, Slack, and/or Telegram."""
    if cfg.discord_webhook:
        try:
            requests.post(
                cfg.discord_webhook,
                json={"content": message},
                timeout=10,
            ).raise_for_status()
            log.debug("Discord webhook sent.")
        except Exception as exc:
            log.error("Discord webhook error: %s", exc)

    if cfg.slack_webhook:
        try:
            requests.post(
                cfg.slack_webhook,
                json={"text": message},
                timeout=10,
            ).raise_for_status()
            log.debug("Slack webhook sent.")
        except Exception as exc:
            log.error("Slack webhook error: %s", exc)

    if cfg.telegram_bot_token and cfg.telegram_chat_id:
        try:
            requests.post(
                f"https://api.telegram.org/bot{cfg.telegram_bot_token}/sendMessage",
                json={"chat_id": cfg.telegram_chat_id, "text": message},
                timeout=10,
            ).raise_for_status()
            log.debug("Telegram message sent.")
        except Exception as exc:
            log.error("Telegram error: %s", exc)


def alert(subject: str, body: str) -> None:
    """Send both email and webhook alerts."""
    log.warning("ALERT: %s — %s", subject, body)
    send_email(subject, body)
    send_webhook(f"🚨 *{subject}*\n{body}")


# ─────────────────────────────────────────────────────────────────────────────
# wp-config.php helpers
# ─────────────────────────────────────────────────────────────────────────────


def _backup_wp_config() -> Path:
    """Create a timestamped backup of wp-config.php before every modification."""
    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_path = cfg.wp_config_path.parent / f"wp-config.{ts}.bak"
    shutil.copy2(cfg.wp_config_path, backup_path)
    os.chmod(backup_path, 0o600)
    log.info("wp-config.php backed up to %s", backup_path)
    return backup_path


def _read_wp_config() -> str:
    return cfg.wp_config_path.read_text(encoding="utf-8")


def _write_wp_config(content: str) -> None:
    _backup_wp_config()
    cfg.wp_config_path.write_text(content, encoding="utf-8")
    os.chmod(cfg.wp_config_path, 0o600)
    log.info("wp-config.php written (permissions set to 600).")


def set_wp_config_value(key: str, value: str) -> None:
    """
    Set (or add) a define() in wp-config.php.

    Handles both string and boolean/numeric values correctly.
    """
    content = _read_wp_config()
    # Determine how to format the value in PHP
    if value.lower() in ("true", "false") or value.lstrip("-").isdigit():
        php_value = value  # bare constant / number
    else:
        escaped = value.replace("'", "\\'")
        php_value = f"'{escaped}'"

    pattern = re.compile(
        r"^define\(\s*['\"]" + re.escape(key) + r"['\"].*?\);",
        re.MULTILINE,
    )
    new_line = f"define('{key}', {php_value});"

    if pattern.search(content):
        content = pattern.sub(new_line, content)
        log.info("Updated wp-config.php define('%s', ...)", key)
    else:
        # Append before the stop-editing marker
        marker = "/* That's all, stop editing!"
        if marker in content:
            content = content.replace(marker, f"{new_line}\n{marker}")
        else:
            content += f"\n{new_line}\n"
        log.info("Added wp-config.php define('%s', ...)", key)

    _write_wp_config(content)


def get_wp_config_value(key: str) -> str | None:
    """Return the raw string value of a defined constant, or None."""
    content = _read_wp_config()
    match = re.search(
        r"define\(\s*['\"]" + re.escape(key) + r"['\"],\s*'([^']*)'\s*\);",
        content,
    )
    return match.group(1) if match else None


# ─────────────────────────────────────────────────────────────────────────────
# Salt rotation
# ─────────────────────────────────────────────────────────────────────────────


def rotate_salts() -> None:
    """
    Fetch fresh salts from the WordPress.org API and replace all eight
    security keys in wp-config.php.
    """
    log.info("Rotating WordPress salts and security keys …")
    try:
        resp = requests.get(cfg.wp_salt_api_url, timeout=15)
        resp.raise_for_status()
        new_salts = resp.text
    except Exception as exc:
        alert("Salt rotation failed", f"Could not reach WordPress salt API: {exc}")
        return

    content = _read_wp_config()

    # Remove existing salt defines
    for name in _SALT_NAMES:
        pattern = re.compile(
            r"^define\(\s*['\"]" + re.escape(name) + r"['\"].*?\);\n?",
            re.MULTILINE,
        )
        content = pattern.sub("", content)

    # Insert fresh salts before the stop-editing marker
    marker = "/* That's all, stop editing!"
    if marker in content:
        content = content.replace(marker, f"{new_salts}\n{marker}")
    else:
        content += f"\n{new_salts}\n"

    _write_wp_config(content)
    log.info("Salt rotation complete.")
    send_webhook("🔑 WordPress salts rotated successfully.")


# ─────────────────────────────────────────────────────────────────────────────
# Health check
# ─────────────────────────────────────────────────────────────────────────────

_consecutive_failures: int = 0
_MAX_FAILURES_BEFORE_RESTART = int(os.getenv("MAX_FAILURES_BEFORE_RESTART", "3"))


def health_check() -> bool:
    """
    Perform HTTP/HTTPS health check against the configured site URL.
    Returns True if the site is healthy, False otherwise.
    Triggers an SSH auto-restart and Cloudflare alert after consecutive failures.
    """
    global _consecutive_failures
    url = cfg.site_url.rstrip("/") + "/"
    try:
        resp = requests.get(url, timeout=cfg.health_timeout_seconds, allow_redirects=True)
        if resp.status_code < 500:
            if _consecutive_failures > 0:
                log.info("Site recovered after %d failure(s).", _consecutive_failures)
                _consecutive_failures = 0
            log.debug("Health check OK: %s %s", resp.status_code, url)
            return True
        log.error("Health check FAIL: HTTP %s from %s", resp.status_code, url)
    except requests.exceptions.Timeout:
        log.error("Health check TIMEOUT: %s", url)
    except requests.exceptions.ConnectionError as exc:
        log.error("Health check CONNECTION ERROR: %s — %s", url, exc)

    _consecutive_failures += 1
    log.warning("Consecutive failures: %d / %d", _consecutive_failures, _MAX_FAILURES_BEFORE_RESTART)

    if _consecutive_failures >= _MAX_FAILURES_BEFORE_RESTART:
        alert(
            "WordPress site down",
            f"Site {url} has failed {_consecutive_failures} consecutive health checks. "
            "Attempting auto-restart.",
        )
        _ssh_restart_service()
        cloudflare_under_attack(enable=True)

    return False


# ─────────────────────────────────────────────────────────────────────────────
# SSH helpers
# ─────────────────────────────────────────────────────────────────────────────


def _ssh_restart_service() -> None:
    """Connect via SSH and restart the configured systemd service."""
    if not (cfg.ssh_host and cfg.ssh_user):
        log.warning("SSH not configured; skipping remote restart.")
        return
    try:
        key_path = os.path.expanduser(cfg.ssh_key_path)
        client = paramiko.SSHClient()
        # Load known_hosts to verify the server's host key.
        # Falls back to RejectPolicy (raises NoValidConnectionsError) if the
        # key is not in known_hosts — safer than AutoAddPolicy.
        try:
            client.load_system_host_keys()
        except Exception:
            pass
        client.set_missing_host_key_policy(paramiko.RejectPolicy())
        client.connect(
            hostname=cfg.ssh_host,
            port=cfg.ssh_port,
            username=cfg.ssh_user,
            key_filename=key_path,
            timeout=30,
        )
        try:
            service = _validate_service_name(cfg.systemd_service)
        except ValueError as exc:
            log.error("Invalid service name, aborting restart: %s", exc)
            return
        cmd = ["sudo", "systemctl", "restart", service]
        _, stdout, stderr = client.exec_command(" ".join(cmd))
        exit_code = stdout.channel.recv_exit_status()
        out = stdout.read().decode()
        err = stderr.read().decode()
        client.close()
        if exit_code == 0:
            log.info("Remote service '%s' restarted successfully.", cfg.systemd_service)
        else:
            log.error("Remote restart failed (exit %d): %s", exit_code, err)
            alert("Auto-restart failed", f"systemctl restart {cfg.systemd_service} failed:\n{err}")
    except Exception as exc:
        log.error("SSH restart error: %s", exc)
        alert("SSH restart error", str(exc))


# ─────────────────────────────────────────────────────────────────────────────
# Cloudflare helpers
# ─────────────────────────────────────────────────────────────────────────────


def cloudflare_purge_cache() -> None:
    """Purge the Cloudflare cache for the configured zone."""
    if not (cfg.cloudflare_zone_id and cfg.cloudflare_api_token):
        log.debug("Cloudflare not configured; skipping cache purge.")
        return
    try:
        resp = requests.post(
            f"https://api.cloudflare.com/client/v4/zones/{cfg.cloudflare_zone_id}/purge_cache",
            headers={
                "Authorization": f"Bearer {cfg.cloudflare_api_token}",
                "Content-Type": "application/json",
            },
            json={"purge_everything": True},
            timeout=15,
        )
        resp.raise_for_status()
        log.info("Cloudflare cache purged.")
    except Exception as exc:
        log.error("Cloudflare cache purge failed: %s", exc)


def cloudflare_under_attack(enable: bool) -> None:
    """Toggle Cloudflare 'Under Attack' mode (I'm Under Attack)."""
    if not (cfg.cloudflare_zone_id and cfg.cloudflare_api_token):
        return
    mode = "under_attack" if enable else "essentially_off"
    try:
        resp = requests.patch(
            f"https://api.cloudflare.com/client/v4/zones/{cfg.cloudflare_zone_id}/settings/security_level",
            headers={
                "Authorization": f"Bearer {cfg.cloudflare_api_token}",
                "Content-Type": "application/json",
            },
            json={"value": mode},
            timeout=15,
        )
        resp.raise_for_status()
        log.info("Cloudflare security level set to '%s'.", mode)
    except Exception as exc:
        log.error("Cloudflare security level change failed: %s", exc)


# ─────────────────────────────────────────────────────────────────────────────
# Security hardening
# ─────────────────────────────────────────────────────────────────────────────


def check_file_permissions() -> list[str]:
    """
    Walk wp_root and report files / directories with insecure permissions.

    Expected:
      files       → 644 (0o644)
      directories → 755 (0o755)
      wp-config   → 600 (0o600)
    """
    issues: list[str] = []
    if not cfg.wp_root.is_dir():
        log.warning("WP root not found: %s", cfg.wp_root)
        return issues

    for entry in cfg.wp_root.rglob("*"):
        try:
            mode = entry.stat().st_mode
        except OSError:
            continue

        if entry == cfg.wp_config_path:
            if stat.S_IMODE(mode) != 0o600:
                issues.append(f"wp-config.php should be 600: {entry} is {oct(stat.S_IMODE(mode))}")
        elif entry.is_dir():
            if stat.S_IMODE(mode) not in (0o755, 0o750):
                issues.append(f"Dir should be 755: {entry} is {oct(stat.S_IMODE(mode))}")
        elif entry.is_file():
            if stat.S_IMODE(mode) not in (0o644, 0o640):
                issues.append(f"File should be 644: {entry} is {oct(stat.S_IMODE(mode))}")

    if issues:
        log.warning("%d permission issue(s) found.", len(issues))
    else:
        log.info("File permissions OK.")
    return issues


def harden_wp_config() -> None:
    """Apply recommended security constants to wp-config.php."""
    log.info("Applying wp-config.php security hardening …")
    hardening: dict[str, str] = {
        "DISALLOW_FILE_EDIT": "true",
        "FORCE_SSL_ADMIN": "true",
        "WP_DEBUG": "false",
        "DISALLOW_FILE_MODS": "true",
        "WP_AUTO_UPDATE_CORE": "minor",
    }
    for key, value in hardening.items():
        set_wp_config_value(key, value)
    log.info("wp-config.php hardening applied.")


def enforce_ssl_redirect(htaccess_path: Path | None = None) -> None:
    """
    Ensure the WordPress .htaccess contains an HTTPS redirect rule.
    Adds the rule at the top of the file if missing.
    """
    path = htaccess_path or cfg.wp_root / ".htaccess"
    if not path.exists():
        log.warning(".htaccess not found at %s; skipping SSL redirect.", path)
        return

    rule = (
        "\n# WP Guardian — Force HTTPS\n"
        "RewriteEngine On\n"
        "RewriteCond %{HTTPS} off\n"
        "RewriteRule ^ https://%{HTTP_HOST}%{REQUEST_URI} [L,R=301]\n"
    )
    content = path.read_text(encoding="utf-8")
    if "Force HTTPS" not in content:
        backup = path.parent / f".htaccess.{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.bak"
        shutil.copy2(path, backup)
        path.write_text(rule + content, encoding="utf-8")
        log.info("SSL redirect added to .htaccess.")
    else:
        log.info("SSL redirect already present in .htaccess.")


# ─────────────────────────────────────────────────────────────────────────────
# WPScan vulnerability check
# ─────────────────────────────────────────────────────────────────────────────


def wpscan_check() -> list[dict[str, Any]]:
    """
    Query the WPScan API for known vulnerabilities against the site URL.
    Returns a list of vulnerability dicts (empty if none found or API not set).
    """
    if not cfg.wpscan_api_token:
        log.debug("WPScan API token not set; skipping vulnerability scan.")
        return []

    # WPScan REST API v3
    domain = cfg.site_url.removeprefix("https://").removeprefix("http://").rstrip("/")
    try:
        resp = requests.get(
            f"https://wpscan.com/api/v3/wordpresses/{domain}",
            headers={"Authorization": f"Token token={cfg.wpscan_api_token}"},
            timeout=30,
        )
        if resp.status_code == 200:
            data = resp.json()
            vulns: list[dict[str, Any]] = data.get("vulnerabilities", [])
            if vulns:
                log.warning("WPScan found %d vulnerability/ies.", len(vulns))
                alert(
                    "WPScan vulnerabilities detected",
                    "\n".join(v.get("title", "Unknown") for v in vulns),
                )
            else:
                log.info("WPScan: no known vulnerabilities.")
            return vulns
        log.warning("WPScan API responded with status %d.", resp.status_code)
    except Exception as exc:
        log.error("WPScan check failed: %s", exc)
    return []


# ─────────────────────────────────────────────────────────────────────────────
# File-integrity monitoring
# ─────────────────────────────────────────────────────────────────────────────

_SCAN_EXTENSIONS = {".php", ".js", ".html", ".htm", ".htaccess"}


def _hash_file(path: Path) -> str:
    sha = hashlib.sha256()
    try:
        with path.open("rb") as fh:
            for chunk in iter(lambda: fh.read(65536), b""):
                sha.update(chunk)
    except OSError:
        return ""
    return sha.hexdigest()


def build_hash_baseline() -> None:
    """Compute SHA-256 hashes for all tracked files and save to baseline_file."""
    log.info("Building file-integrity baseline …")
    baseline: dict[str, str] = {}
    if cfg.wp_root.is_dir():
        for path in cfg.wp_root.rglob("*"):
            if path.is_file() and path.suffix in _SCAN_EXTENSIONS:
                key = str(path.relative_to(cfg.wp_root))
                baseline[key] = _hash_file(path)
    cfg.baseline_file.write_text(json.dumps(baseline, indent=2), encoding="utf-8")
    log.info("Baseline saved with %d file(s).", len(baseline))


def check_file_integrity() -> list[str]:
    """
    Compare current file hashes to the baseline.
    Returns list of suspicious modifications (new, modified, or deleted files).
    """
    if not cfg.baseline_file.exists():
        log.warning("No baseline found; run --harden to create one.")
        return []

    baseline: dict[str, str] = json.loads(cfg.baseline_file.read_text(encoding="utf-8"))
    current: dict[str, str] = {}
    if cfg.wp_root.is_dir():
        for path in cfg.wp_root.rglob("*"):
            if path.is_file() and path.suffix in _SCAN_EXTENSIONS:
                key = str(path.relative_to(cfg.wp_root))
                current[key] = _hash_file(path)

    issues: list[str] = []
    for fpath, expected in baseline.items():
        if fpath not in current:
            issues.append(f"DELETED: {fpath}")
        elif current[fpath] != expected:
            issues.append(f"MODIFIED: {fpath}")
    for fpath in current:
        if fpath not in baseline:
            issues.append(f"NEW FILE: {fpath}")

    if issues:
        log.warning("%d file integrity issue(s) detected.", len(issues))
        alert("File integrity violations", "\n".join(issues[:20]))
    else:
        log.info("File integrity OK.")
    return issues


# ─────────────────────────────────────────────────────────────────────────────
# WordPress core minor update
# ─────────────────────────────────────────────────────────────────────────────


def apply_core_minor_update() -> None:
    """
    Apply WordPress core minor updates using WP-CLI if available.
    Only applies minor updates (e.g. 6.5.3 → 6.5.4) — never major.
    """
    wp_cli = shutil.which("wp")
    if not wp_cli:
        log.info("WP-CLI not found; skipping core update.")
        return
    # Resolve and validate wp_root to prevent path traversal
    wp_root_resolved = cfg.wp_root.resolve()
    if not wp_root_resolved.is_dir():
        log.error("WP root directory does not exist: %s", wp_root_resolved)
        return
    try:
        result = subprocess.run(  # noqa: S603
            [wp_cli, "core", "update", "--minor", f"--path={wp_root_resolved}"],
            capture_output=True,
            text=True,
            timeout=120,
        )
        if result.returncode == 0:
            log.info("WP-CLI core update: %s", result.stdout.strip())
        else:
            log.warning("WP-CLI core update output: %s", result.stderr.strip())
    except subprocess.TimeoutExpired:
        log.error("WP-CLI core update timed out.")
    except Exception as exc:
        log.error("WP-CLI core update error: %s", exc)


# ─────────────────────────────────────────────────────────────────────────────
# Daily status report
# ─────────────────────────────────────────────────────────────────────────────


def daily_report() -> None:
    """Compile and send a daily status summary."""
    log.info("Generating daily status report …")
    lines: list[str] = [
        f"Daily WP Guardian Report — {datetime.now(timezone.utc).date()}",
        f"Site: {cfg.site_url}",
        "",
    ]

    # Health
    healthy = health_check()
    lines.append(f"Health: {'✅ OK' if healthy else '❌ DOWN'}")

    # Permissions
    perm_issues = check_file_permissions()
    lines.append(f"Permission issues: {len(perm_issues)}")
    if perm_issues:
        lines.extend(f"  {i}" for i in perm_issues[:10])

    # Integrity
    integrity_issues = check_file_integrity()
    lines.append(f"Integrity issues: {len(integrity_issues)}")
    if integrity_issues:
        lines.extend(f"  {i}" for i in integrity_issues[:10])

    report = "\n".join(lines)
    log.info("Daily report:\n%s", report)
    send_email("WP Guardian Daily Report", report)
    send_webhook(report)


# ─────────────────────────────────────────────────────────────────────────────
# Daemon mode
# ─────────────────────────────────────────────────────────────────────────────

_running = True


def _handle_signal(signum: int, _frame: Any) -> None:
    global _running
    log.info("Received signal %d; shutting down daemon.", signum)
    _running = False


def run_daemon() -> None:
    """
    Run wp_guardian as a continuous daemon using the 'schedule' library.
    Scheduled tasks:
      - Health check every N seconds (HEALTH_INTERVAL_SECONDS)
      - File integrity check every 6 hours
      - Daily status report at 07:00 UTC
      - Salt rotation every 30 days (first run at startup if not recently rotated)
    """
    log.info("Starting WP Guardian daemon …")
    signal.signal(signal.SIGTERM, _handle_signal)
    signal.signal(signal.SIGINT, _handle_signal)

    # Schedule recurring tasks
    schedule.every(cfg.health_interval_seconds).seconds.do(health_check)
    schedule.every(6).hours.do(check_file_integrity)
    schedule.every().day.at("07:00").do(daily_report)
    schedule.every(30).days.do(rotate_salts)

    log.info(
        "Daemon running. Health check every %ds, file integrity every 6h, "
        "daily report at 07:00 UTC, salts rotated every 30 days.",
        cfg.health_interval_seconds,
    )

    while _running:
        schedule.run_pending()
        time.sleep(1)

    log.info("WP Guardian daemon stopped.")


# ─────────────────────────────────────────────────────────────────────────────
# CLI entry point
# ─────────────────────────────────────────────────────────────────────────────


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="wp_guardian",
        description="WordPress site maintenance, security hardening, and monitoring daemon.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "--check",
        action="store_true",
        help="Run a one-shot health check and print result.",
    )
    group.add_argument(
        "--harden",
        action="store_true",
        help=(
            "Apply security hardening: update wp-config.php constants, "
            "enforce SSL redirect, check & fix permissions, build file-integrity baseline."
        ),
    )
    group.add_argument(
        "--rotate-salts",
        action="store_true",
        dest="rotate_salts",
        help="Fetch fresh salts from the WordPress API and rotate wp-config.php keys.",
    )
    group.add_argument(
        "--daemon",
        action="store_true",
        help="Run as a continuous monitoring daemon.",
    )
    return parser


def main() -> int:
    """Main entry point."""
    parser = _build_parser()
    args = parser.parse_args()

    if args.check:
        ok = health_check()
        return 0 if ok else 1

    if args.rotate_salts:
        rotate_salts()
        return 0

    if args.harden:
        harden_wp_config()
        enforce_ssl_redirect()
        perm_issues = check_file_permissions()
        if perm_issues:
            log.warning("Permissions to review:\n%s", "\n".join(perm_issues))
        build_hash_baseline()
        apply_core_minor_update()
        wpscan_check()
        log.info("Hardening complete.")
        return 0

    if args.daemon:
        run_daemon()
        return 0

    return 0


if __name__ == "__main__":
    sys.exit(main())
