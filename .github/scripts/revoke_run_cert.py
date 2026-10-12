"""Отзывает сертификат Apple Development, который создал этот прогон CI.

Xcode с -allowProvisioningUpdates на каждой новой машине CI выпускает свой
сертификат («Created via API»). Закрытый ключ умирает вместе с машиной, а
сертификат остаётся и занимает лимит аккаунта — на десятом сборка падает
«Your account has reached the maximum number of certificates» (сборка 146,
12.10.2026). Подписанные сборки это не трогает: TestFlight/App Store подписаны
дистрибутивным сертификатом.

Свой сертификат ищем по серийному номеру в связке ключей этой машины — чужой
параллельный прогон не задеваем. Если в связке ничего нет, отзываем «Created
via API», выпущенные после старта прогона (RUN_STARTED, ISO UTC).

Окружение: ASC_KEY_ID, ASC_ISSUER_ID, ключ в AuthKey.p8, RUN_STARTED.
"""
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

import jwt

KEY = open("AuthKey.p8").read()


def token():
    now = int(time.time())
    return jwt.encode({"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 900,
                       "aud": "appstoreconnect-v1"}, KEY, algorithm="ES256",
                      headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"})


def api(method, path):
    req = urllib.request.Request("https://api.appstoreconnect.apple.com" + path, method=method,
                                 headers={"Authorization": "Bearer " + token()})
    try:
        with urllib.request.urlopen(req) as f:
            body = f.read()
            return f.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        return e.code, {}


def norm(serial):
    return serial.strip().upper().lstrip("0")


def local_serials():
    try:
        pem = subprocess.run(["security", "find-certificate", "-a", "-c", "Apple Development", "-p"],
                             capture_output=True, text=True).stdout
    except OSError:
        return set()
    out = set()
    for block in re.findall(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", pem, re.S):
        s = subprocess.run(["openssl", "x509", "-noout", "-serial"], input=block,
                           capture_output=True, text=True).stdout
        if "=" in s:
            out.add(norm(s.split("=", 1)[1]))
    return out


def main():
    code, r = api("GET", "/v1/certificates?limit=200&filter[certificateType]=DEVELOPMENT")
    if code != 200:
        print(f"::warning::список сертификатов не получен ({code}) — ничего не отзываем")
        return
    ours = [c for c in r.get("data", []) if c["attributes"].get("displayName") == "Created via API"]
    mine = local_serials()
    targets = [c for c in ours if norm(c["attributes"].get("serialNumber", "")) in mine]
    if not targets:
        # Связка пуста (сертификат в облаке Apple) — берём выпущенные после старта прогона.
        # Срок у сертификата разработчика ровно год от выпуска.
        started = datetime.fromisoformat(os.environ["RUN_STARTED"].replace("Z", "+00:00")) - timedelta(minutes=2)
        for c in ours:
            exp = datetime.fromisoformat(c["attributes"]["expirationDate"].replace("Z", "+00:00"))
            issued = exp.replace(year=exp.year - 1)
            if issued.astimezone(timezone.utc) >= started:
                targets.append(c)
    if not targets:
        print("сертификатов этого прогона не нашли — отзывать нечего")
        return
    for c in targets:
        code, _ = api("DELETE", "/v1/certificates/" + c["id"])
        print(f"отзыв {c['id']} ({c['attributes'].get('serialNumber')}): {'ок' if code == 204 else code}")


main()
