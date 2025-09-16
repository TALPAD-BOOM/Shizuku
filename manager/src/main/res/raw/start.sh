#!/system/bin/sh

echo "info: start.sh begin"

pm grant com.tal.pad.program android.permission.WRITE_SECURE_SETTINGS
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

appops set --uid bin.mt.plus REQUEST_INSTALL_PACKAGES allow
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

i=1
while [ $(($i)) -le 5 ]; do
  pgrep -cf shizuku_server > /dev/null
  result=$?
  if [ ${result} -ne 0 ]; then
    echo "info: Attempt #$i to run shizuku_starter..."
    $1 $2
    result=$?
    if [ ${result} -ne 0 ]; then
        echo "info: shizuku_starter exit with non-zero value $result"
        exit ${result}
    fi
  fi
  sleep 1
  i=$((i + 1))
done

pgrep -cf shizuku_server > /dev/null
result=$?
if [ ${result} -ne 0 ]; then
  echo "info: shizuku_starter was killed after 5 attempts"
  exit 9
fi