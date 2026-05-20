if [[ -n "${GOOGLE_SERVICES:-}" ]]; then
    printf '%s' "$GOOGLE_SERVICES" > app/google-services.json
fi

if [[ -n "${SIGNING_KEY:-}" ]]; then
    printf '%s' "$SIGNING_KEY" | base64 -d > key.jks
    echo "storeFile=key.jks
    storePassword=$KEY_STORE_PASSWORD
    keyAlias=$ALIAS
    keyPassword=$KEY_PASSWORD" >signing.properties
fi
