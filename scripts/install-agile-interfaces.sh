#!/bin/sh
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

set -e

CURRDIR=`pwd`
DEPS=${1:-$CURRDIR/deps}
BUILD=$DEPS/build

if [ ! -e "$BUILD" ] ; then
  mkdir -p $BUILD
fi

cd agile-dbus-java-interface
./scripts/install-dependencies.sh $DEPS
ln -s $DEPS deps
mvn package
cp target/agile-interfaces-1.0.jar $DEPS
cd ..

cd $DEPS

mvn install:install-file -Dfile=$DEPS/agile-interfaces-1.0.jar \
                         -DgroupId=org.eclipse.agail \
                         -DartifactId=agile-interfaces \
                         -Dversion=1.0 \
                         -Dpackaging=jar \
                         -DgeneratePom=true \
                         -DlocalRepositoryPath=$DEPS
