import sys, json
with open("local/%s.google" % sys.argv[1]) as json_file:
    data=json.load(json_file)
if sys.argv[2] == "mobilesdk_app_id":
    for x in data["client"]:
        if x["client_info"]["android_client_info"]["package_name"] == sys.argv[1]:
            print(x["client_info"]["mobilesdk_app_id"])
elif sys.argv[2] == "fcm_api_key":
    for x in data["client"]:
        if x["client_info"]["android_client_info"]["package_name"] == sys.argv[1]:
            print(x["api_key"][0]["current_key"])
elif sys.argv[2] == "project_id":
    print(data["project_info"]["project_id"])
else:
    print(data["project_info"]["project_number"])
