# syntax=docker/dockerfile:1
#
# Builds the Better Red release APK in a container: no Android Studio or SDK needed on the machine.
#
#   make apk                       (the easy way: see the Makefile and docs/releasing.md)
#
# or by hand (podman works the same):
#
#   docker build --target apk --output type=local,dest=dist \
#     --secret id=keystore,src=/path/to/release.jks \
#     --secret id=keystore_password,env=BR_KEYSTORE_PASSWORD \
#     --secret id=key_alias,env=BR_KEY_ALIAS \
#     --secret id=key_password,env=BR_KEY_PASSWORD .
#
# Signing keys arrive as build secrets: they are never stored in an image layer. Without them the
# APK is built unsigned (dist/better-red-<version>-unsigned.apk).

# ---- sdk: JDK + Android SDK (cached as a layer; changes only when the versions below change)
FROM docker.io/library/eclipse-temurin:21-jdk-noble AS sdk
ARG CMDLINE_TOOLS=13114758
ARG CMDLINE_TOOLS_SHA256=7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee
# Keep in step with compileSdk in app/build.gradle.kts and the build tools AGP asks for.
ARG PLATFORM=platforms;android-37.0
ARG BUILD_TOOLS=build-tools;36.0.0
ENV ANDROID_HOME=/opt/android-sdk \
    GRADLE_OPTS=-Dorg.gradle.daemon=false
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl unzip \
 && rm -rf /var/lib/apt/lists/*
RUN curl -fsSLo /tmp/clt.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" \
 && echo "${CMDLINE_TOOLS_SHA256}  /tmp/clt.zip" | sha256sum -c - \
 && unzip -q /tmp/clt.zip -d /tmp/clt \
 && mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && mv /tmp/clt/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" \
 && rm -rf /tmp/clt /tmp/clt.zip
RUN yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
 && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "$PLATFORM" "$BUILD_TOOLS" > /dev/null

# ---- build
FROM sdk AS build
WORKDIR /src
# Gradle itself and the build plugins first, so source changes don't download them again.
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY app/build.gradle.kts app/
RUN ./gradlew --version > /dev/null && ./gradlew help > /dev/null
COPY . .
RUN --mount=type=secret,id=keystore,required=false \
    --mount=type=secret,id=keystore_password,required=false \
    --mount=type=secret,id=key_alias,required=false \
    --mount=type=secret,id=key_password,required=false \
    set -eu; \
    if [ -s /run/secrets/keystore ]; then \
      export BR_KEYSTORE_FILE=/run/secrets/keystore; \
      export BR_KEYSTORE_PASSWORD="$(cat /run/secrets/keystore_password)"; \
      export BR_KEY_ALIAS="$(cat /run/secrets/key_alias)"; \
      if [ -s /run/secrets/key_password ]; then export BR_KEY_PASSWORD="$(cat /run/secrets/key_password)"; fi; \
    else \
      echo "No signing key given: building an UNSIGNED APK"; \
    fi; \
    ./gradlew testDebugUnitTest assembleRelease; \
    version="$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)"; \
    mkdir -p /out; \
    if [ -f app/build/outputs/apk/release/app-release.apk ]; then \
      cp app/build/outputs/apk/release/app-release.apk "/out/better-red-$version.apk"; \
      "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "/out/better-red-$version.apk" 2>/dev/null \
        | grep 'SHA-256' > /out/signing-certificate.txt; \
    else \
      cp app/build/outputs/apk/release/app-release-unsigned.apk "/out/better-red-$version-unsigned.apk"; \
    fi; \
    cd /out && sha256sum *.apk > SHA256SUMS

# ---- apk: only the results, for --output type=local,dest=dist
FROM scratch AS apk
COPY --from=build /out/ /
