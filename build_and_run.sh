#!/bin/bash

if [[ "$1" == "" ]]; then
	echo "Usage: $(basename "$0") appID"
	exit 1
fi

app="$(basename "$1")"
cd "$(dirname "$0")"
shift;

if [[ ! -e local/$app.settings ]]; then
	echo "App settings for '$app' not found at 'local/$app.settings'!"
	exit 2
fi

. local/$app.settings

build="$gradleBuild"
if [[ "$@" != "" ]]; then
	build=""
fi

export ORG_GRADLE_PROJECT_gcm_defaultSenderId="$(python3 load_value.py $app "project_number")"
export ORG_GRADLE_PROJECT_google_app_id="$(python3 load_value.py $app "mobilesdk_app_id")"
export ORG_GRADLE_PROJECT_project_id="$(python3 load_value.py $app "project_id")"
export ORG_GRADLE_PROJECT_fcm_api_key="$(python3 load_value.py $app "fcm_api_key")"

echo "isBeta = '$ORG_GRADLE_PROJECT_isBeta'"
echo "appName = '$ORG_GRADLE_PROJECT_appName'"
echo "app_id = '$ORG_GRADLE_PROJECT_app_id'"
echo "mAppSecret = '$ORG_GRADLE_PROJECT_mAppSecret'"
echo "gcm_defaultSenderId = '$ORG_GRADLE_PROJECT_gcm_defaultSenderId'"
echo "google_app_id = '$ORG_GRADLE_PROJECT_google_app_id'"
echo "project_id = '$ORG_GRADLE_PROJECT_project_id'"
echo "fcm_api_key = '$ORG_GRADLE_PROJECT_fcm_api_key'"
echo "app_server = '$ORG_GRADLE_PROJECT_app_server'"
echo "push_module_identification = '$ORG_GRADLE_PROJECT_push_module_identification'"
echo "version_name = '$ORG_GRADLE_PROJECT_version_name'"
echo "version_code = '$ORG_GRADLE_PROJECT_version_code'"
echo "signing with key '$ORG_GRADLE_PROJECT_mKeyAlias'"
echo ""

./gradlew $build $@ || exit 1
real_apk="$(realpath $apk)"
dest="signed-${ORG_GRADLE_PROJECT_app_id}.apk"
# jarsigner -keystore "$ORG_GRADLE_PROJECT_mStoreFile" -storepass "$ORG_GRADLE_PROJECT_mStorePassword" -keypass "$ORG_GRADLE_PROJECT_mKeyPassword" "$real_apk" "$ORG_GRADLE_PROJECT_mKeyAlias" && $ANDROID_HOME/build-tools/28.0.3/zipalign -f 4 "$real_apk" "$dest"
# echo "Please input password: '$ORG_GRADLE_PROJECT_mStorePassword'..."
(sleep 2; echo "$ORG_GRADLE_PROJECT_mStorePassword") | apksigner sign -ks "$ORG_GRADLE_PROJECT_mStoreFile" -in "$real_apk" -out "$dest" && rm *.idsig && echo "Installing '$dest'..." && adb install -r "$dest" && (
#adb install -r "$real_apk" && (
	sleep 1
	trap "adb shell am force-stop $app & exit" INT
	adb shell am start -n $app/$activity &
	while true; do
		PID=""
		while [ -z "$PID" ]; do
			PID="$(adb shell ps | grep $app | tr -s [:space:] ' ' | cut -d' ' -f2)"
			sleep 0.1
		done
		echo "Found PID: $PID"
		adb logcat -T 128 -v threadtime | egrep "($PID)"
	done
)
