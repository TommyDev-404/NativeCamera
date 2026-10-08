-- for inspecting sqlite db
adb shell run-as com.mangtommy.NativeCamera ls -lh files/SQLite/

-- to remove the db
adb shell run-as com.mangtommy.NativeCamera rm -f \
  files/SQLite/estampmo.db \
  files/SQLite/estampmo.db-wal \
  files/SQLite/estampmo.db-shm \

-- view feature hub storage
adb shell run-as com.mangtommy.NativeCamera ls -l files/face_hub/Pikachu/

-- remove the feature hub data
adb shell run-as com.mangtommy.NativeCamera rm -f files/face_hub/Pikachu/features.db

-- stop the app
adb shell am force-stop com.mangtommy.NativeCamera

-- create the folder where u put the copied db
mkdir -p /tmp/testdb

-- copy the db into a temporary folder to open
adb shell run-as com.mangtommy.NativeCamera cat files/SQLite/test.db > /tmp/testdb/test.db
adb shell run-as com.mangtommy.NativeCamera cat files/SQLite/test.db-wal > /tmp/testdb/test.db-wal
adb shell run-as com.mangtommy.NativeCamera cat files/SQLite/test.db-shm > /tmp/testdb/test.db-shm

-- open the copied db
sqlite3 test.db

-- create an auto copy of current changes in db
For example, create:

nano ~/copy-estampmo-db.sh

Put:
#!/bin/bash

PACKAGE="com.mangtommy.NativeCamera"

REMOTE="databases/estampmo.db"
REMOTE_JOURNAL="databases/estampmo.db-journal"

LOCAL="/tmp/estampmo-db"

adb shell am force-stop "$PACKAGE"

rm -rf "$LOCAL"
mkdir -p "$LOCAL"

adb shell run-as "$PACKAGE" cat "$REMOTE" > "$LOCAL/estampmo.db"

if adb shell run-as "$PACKAGE" test -f "$REMOTE_JOURNAL"; then
    adb shell run-as "$PACKAGE" cat "$REMOTE_JOURNAL" > "$LOCAL/estampmo.db-journal"
fi

cd "$LOCAL"

sqlite3 estampmo.db
Then:

chmod +x ~/copy-estampmo-db.sh

Whenever you want to inspect the latest database:

~/copy-estampmo-db.sh

That way you don't have to think


-- EXPO SQLITE
#!/bin/bash

PACKAGE="com.mangtommy.NativeCamera"

REMOTE="files/SQLite/estampmo.db"
REMOTE_WAL="files/SQLite/estampmo.db-wal"
REMOTE_SHM="files/SQLite/estampmo.db-shm"

LOCAL="/tmp/estampmo-db"

adb shell am force-stop "$PACKAGE"

rm -rf "$LOCAL"
mkdir -p "$LOCAL"

echo "Copying Expo SQLite database..."

adb shell run-as "$PACKAGE" cat "$REMOTE" > "$LOCAL/estampmo.db"

if adb shell run-as "$PACKAGE" test -f "$REMOTE_WAL"; then
    adb shell run-as "$PACKAGE" cat "$REMOTE_WAL" > "$LOCAL/estampmo.db-wal"
fi

if adb shell run-as "$PACKAGE" test -f "$REMOTE_SHM"; then
    adb shell run-as "$PACKAGE" cat "$REMOTE_SHM" > "$LOCAL/estampmo.db-shm"
fi

if [ ! -s "$LOCAL/estampmo.db" ]; then
    echo "Failed to copy database."
    exit 1
fi

echo "Database copied to $LOCAL/estampmo.db"
echo "Opening SQLite..."

cd "$LOCAL"

sqlite3 estampmo.db

-- make it executable
chmod +x ~/expo-estampmo-db.sh

-- run it

~/expo-estampmo-db.sh