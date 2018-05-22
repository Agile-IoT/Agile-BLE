#-------------------------------------------------------------------------------
# Copyright (C) 2017 Create-Net / FBK.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License 2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-2.0/
# 
# SPDX-License-Identifier: EPL-2.0
# 
# Contributors:
#     Create-Net / FBK - initial API and implementation
#-------------------------------------------------------------------------------

ARG BASEIMAGE_BUILD=agileiot/raspberry-pi3-zulujdk:8-jdk-maven
ARG BASEIMAGE_DEPLOY=agileiot/raspberry-pi3-zulujdk:8-jre

FROM $BASEIMAGE_BUILD

# Add packages
RUN apt-get update && apt-get install --no-install-recommends -y \
    build-essential \
    git\
    ca-certificates \
    apt \
    software-properties-common \
    unzip \
    cpp \
    binutils \
    maven \
    gettext \
    libc6-dev \
    make \
    cmake \
    cmake-data \
    pkg-config \
    clang \
    gcc-4.9 \
    g++-4.9 \
    qdbus \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# resin-sync will always sync to /usr/src/app, so code needs to be here.
WORKDIR /usr/src/app
ENV APATH /usr/src/app

COPY scripts scripts

COPY agile-dbus-java-interface agile-dbus-java-interface
RUN CC=clang CXX=clang++ CMAKE_C_COMPILER=clang CMAKE_CXX_COMPILER=clang++ \
scripts/install-agile-interfaces.sh $APATH/deps

RUN apt-get update && apt-get install --no-install-recommends -y \
    libbluetooth-dev \
    libudev-dev \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN apt-get update && apt-get install --no-install-recommends -y \
    libglib2.0-0=2.42.1-1+b1 \
    libglib2.0-dev=2.42.1-1+b1 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN PKG_CONFIG_PATH=/usr/local/lib/pkgconfig:/usr/lib/pkgconfig CC=clang CXX=clang++ CMAKE_C_COMPILER=clang CMAKE_CXX_COMPILER=clang++ \
scripts/install-tinyb.sh $APATH/deps

# we need dbus-launch
RUN apt-get update && apt-get install --no-install-recommends -y \
    dbus-x11 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# required by tinyb JNI
RUN apt-get update && apt-get install --no-install-recommends -y \
    libxrender1 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# isntall bluez
RUN echo "deb http://deb.debian.org/debian unstable main" >>/etc/apt/sources.list \
    && apt-get update && apt-get install --no-install-recommends -y \
    bluez/unstable \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# copy directories into WORKDIR
COPY org.eclipse.agail.protocol.BLE org.eclipse.agail.protocol.BLE

RUN mvn package -f ./org.eclipse.agail.protocol.BLE/pom.xml 

FROM $BASEIMAGE_DEPLOY
WORKDIR /usr/src/app
ENV APATH /usr/src/app

# install services
RUN echo "deb http://deb.debian.org/debian unstable main" >>/etc/apt/sources.list \
    && apt-get update && apt-get install --no-install-recommends -y \
    bluez/unstable \
    dbus \
    qdbus \
    libxrender1 \
    libxext6 \
    libxtst6 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=0 $APATH/scripts scripts
COPY --from=0 $APATH/org.eclipse.agail.protocol.BLE/target/ble-1.0-jar-with-dependencies.jar org.eclipse.agail.protocol.BLE/target/ble-1.0-jar-with-dependencies.jar
COPY --from=0 $APATH/deps deps

# workaround for external startup command. To be removed.
RUN mkdir -p /usr/local/libexec/bluetooth/ && ln -s /usr/sbin/bluetoothd /usr/local/libexec/bluetooth/bluetoothd

CMD [ "bash", "/usr/src/app/scripts/start.sh" ]
