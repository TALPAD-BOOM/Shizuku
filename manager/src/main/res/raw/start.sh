#!/system/bin/sh

echo "info: start.sh begin"

pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS
result=$?
if [ ${result} -ne 0 ]; then
    echo "info: Shizuku granted WRITE_SECURE_SETTINGS exit with non-zero value $result"
else
    echo "info: Shizuku granted WRITE_SECURE_SETTINGS exit with 0"
fi

appops set --uid com.rosan.installer.x.revived MANAGE_EXTERNAL_STORAGE allow
result=$?
if [ ${result} -ne 0 ]; then
    echo "info: InstallerX granted MANAGE_EXTERNAL_STORAGE exit with non-zero value $result"
else
    echo "info: InstallerX granted MANAGE_EXTERNAL_STORAGE exit with 0"
fi

appops set --uid bin.mt.plus MANAGE_EXTERNAL_STORAGE allow
result=$?
if [ ${result} -ne 0 ]; then
    echo "info: MT file manager granted MANAGE_EXTERNAL_STORAGE exit with non-zero value $result"
else
    echo "info: MT file manager granted MANAGE_EXTERNAL_STORAGE exit with 0"
fi

pm grant bin.mt.plus android.permission.REQUEST_INSTALL_PACKAGES
result=$?
if [ ${result} -ne 0 ]; then
    echo "info: MT file manager granted REQUEST_INSTALL_PACKAGES exit with non-zero value $result"
else
    echo "info: MT file manager granted REQUEST_INSTALL_PACKAGES exit with 0"
fi

if [ -f "/sdcard/talpad.sh" ]; then
  /system/bin/sh "/sdcard/talpad.sh"
  result=$?
  if [ ${result} -ne 0 ]; then
      echo "info: talpad.sh exit with non-zero value $result"
  else
      echo "info: talpad.sh exit with 0"
  fi
fi