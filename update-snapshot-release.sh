#!/bin/bash -e

latest=$(curl -fs 'https://repo-api.sleeping.town/com/unascribed/unsup/maven-metadata.xml' |xq -r '.metadata.versioning.latest')
snap=
snapurl=
if [[ "$latest" =~ -SNAPSHOT$ ]]; then
	snap=$(curl -fs "https://repo-api.sleeping.town/com/unascribed/unsup/$latest/maven-metadata.xml" |xq -r '.metadata.versioning.snapshot|"\(.timestamp)-\(.buildNumber)"')
	snapurl="https://repo.sleeping.town/com/unascribed/unsup/$latest/unsup-${latest%-SNAPSHOT}-$snap"
	cat > com.unascribed.unsup.json <<EOF
{
	"formatVersion": 1,
	"name": "unsup",
	"uid": "com.unascribed.unsup",
	"version": "$latest",
	"+agents": [
		{
			"name": "com.unascribed:unsup:$latest",
			"MMC-absoluteUrl": "$snapurl.jar"
		}
	]
}
EOF
else
	exit 1
fi


auth="Authorization: token $FORGEJO_KEY"
curl -s -X DELETE -H "$auth" https://git.sleeping.town/api/v1/repos/unascribed/unsup/releases/tags/SNAPSHOT >/dev/null
curl -s -X DELETE -H "$auth" https://git.sleeping.town/api/v1/repos/unascribed/unsup/tags/SNAPSHOT >/dev/null

lasttag=$(curl -s 'https://git.sleeping.town/api/v1/repos/unascribed/unsup/releases?draft=false&pre-release=true&page=1&limit=1' |jq '.[0].tag_name')

resp=$(curl -s -X POST -H 'Content-Type: application/json' -H "$auth" https://git.sleeping.town/api/v1/repos/unascribed/unsup/releases --data-raw "
{
	\"name\": \"v$latest\",
	\"target_commitish\": \"${CI_COMMIT_SHA-trunk}\",
	\"tag_name\": \"SNAPSHOT\",
	\"draft\": true,
	\"prerelease\": true,
	\"body\": \"Autogenerated snapshot release. See [the commit log](https://git.sleeping.town/unascribed/unsup/compare/$lasttag...trunk) for changes.\\n\\n***This contains the latest changes, fresh off the trunk. It may be unstable and/or completely broken.***\\n\\n**Note**: Snapshots are signed with a different key from releases.\"
}")
echo 'Release create response:'
echo "$resp" |jq .
relid=$(echo "$resp" | jq -r .id)

if [[ -z "$relid" || "$relid" == "null" ]]; then
	exit 1
fi

echo 'Jar attach response:'
curl --fail-with-body -s -X POST -H "$auth" "https://git.sleeping.town/api/v1/repos/unascribed/unsup/releases/$relid/assets?name=unsup-$latest.jar" -F "external_url=$snapurl.jar" |jq .
echo 'Sig attach response:'
curl --fail-with-body -s -X POST -H "$auth" "https://git.sleeping.town/api/v1/repos/unascribed/unsup/releases/$relid/assets?name=unsup-$latest.jar.sig" -F "external_url=$snapurl.sig" |jq .
echo 'Component attach response:'
curl --fail-with-body -s -X POST -H "$auth" "https://git.sleeping.town/api/v1/repos/unascribed/unsup/releases/$relid/assets?name=com.unascribed.unsup.json" -F "attachment=@com.unascribed.unsup.json" |jq .

echo 'Undraft response:'
curl --fail-with-body -s -X PATCH -H 'Content-Type: application/json' -H "$auth" "https://git.sleeping.town/api/v1/repos/unascribed/unsup/releases/$relid" --data-raw '{"draft": false}' |jq .
