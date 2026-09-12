FROM eclipse-temurin:17-jdk

ENV ANDROID_HOME=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle \
    PATH=/opt/gradle/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update && apt-get install -y --no-install-recommends unzip curl ca-certificates git \
    && rm -rf /var/lib/apt/lists/*

# Gradle 8.7
RUN curl -fsSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip \
    && unzip -q /tmp/gradle.zip -d /opt && mv /opt/gradle-8.7 /opt/gradle && rm /tmp/gradle.zip

# Android commandline tools + SDK packages
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && curl -fsSL -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q /tmp/cmdtools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm /tmp/cmdtools.zip \
    && yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

WORKDIR /project
