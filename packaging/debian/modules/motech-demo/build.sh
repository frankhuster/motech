#!/bin/sh

# Exit on non-zero exit code
set -e

# Motech demo
echo "====================="
echo "Building motech-demo"
echo "====================="

MODULE_DIR=$PACKAGING_DIR/modules/motech-demo

# Copy control
mkdir -p $TMP_DIR/motech-demo/DEBIAN
cp $MODULE_DIR/control $TMP_DIR/motech-demo/DEBIAN/control
# Copy copyright
mkdir -p $TMP_DIR/motech-demo/usr/share/doc/motech-demo
cp $PACKAGING_DIR/motech/usr/share/doc/motech/copyright $TMP_DIR/motech-demo/usr/share/doc/motech-demo/copyright
# Copy changelog
cp $MODULE_DIR/changelog* $TMP_DIR/motech-demo/usr/share/doc/motech-demo
gzip --best $TMP_DIR/motech-demo/usr/share/doc/motech-demo/changelog*
# Copy bundle
mkdir -p $TMP_DIR/motech-demo/$BUNDLE_DIR
cp $MOTECH_BASE/motech-demo/target/motech-demo-*.jar $TMP_DIR/motech-demo/$BUNDLE_DIR
# Copy scripts
cp $MODULE_DIR/../common/post* $TMP_DIR/motech-demo/DEBIAN

# Permissions
find $TMP_DIR/motech-demo -type d | xargs chmod 755 # directories
find $TMP_DIR/motech-demo -type f | xargs chmod 644 # files
chmod 755 $TMP_DIR/motech-demo/DEBIAN/*

# Build
echo "Building package"
PACKAGE_NAME=motech-demo_$VERSION.deb
fakeroot dpkg-deb --build motech-demo
mv motech-demo.deb $PACKAGING_DIR/target/$PACKAGE_NAME

# Check for problems
echo "Checking package with lintian"
lintian -i $PACKAGING_DIR/target/$PACKAGE_NAME

# Clean up
rm -r $TMP_DIR/motech-demo

echo "Done. Finished building $PACKAGE_NAME"


