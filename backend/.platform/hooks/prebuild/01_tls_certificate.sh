#!/bin/bash
# Nginx가 쓸 Cloudflare 원본 인증서를 S3에서 내려받는다.
# hooks/(코드 배포)와 confighooks/(환경 속성 등 설정 변경)에 같은 파일을 둔다. 설정 변경 때도 Nginx가 다시 시작하기 때문이다.
# 환경 속성 TLS_CERT_S3_URI에 인증서 위치를 적는다. 예: s3://elasticbeanstalk-ap-northeast-2-<계정 ID>/tls/origin
#   그 위치에 origin.crt(인증서)와 origin.key(개인 키)를 올려 둔다.
# Elastic Beanstalk의 기본 인스턴스 역할은 elasticbeanstalk-로 시작하는 버킷을 읽을 수 있다.
set -euo pipefail

uri=$(/opt/elasticbeanstalk/bin/get-config environment -k TLS_CERT_S3_URI)
if [ -z "$uri" ]; then
    echo "환경 속성 TLS_CERT_S3_URI가 없습니다." >&2
    exit 1
fi

aws s3 cp "$uri/origin.crt" /etc/pki/tls/certs/origin.crt
aws s3 cp "$uri/origin.key" /etc/pki/tls/private/origin.key
chmod 644 /etc/pki/tls/certs/origin.crt
chmod 600 /etc/pki/tls/private/origin.key
