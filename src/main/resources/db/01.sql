CREATE TABLE "app_user" (
  "localpart" TEXT PRIMARY KEY
);

CREATE TABLE "transaction" (
  "id" TEXT PRIMARY KEY,
  "started" TIMESTAMP WITH TIMEZONE,
  "processed" TIMESTAMP WITH TIMEZONE
);

CREATE TABLE "multi_user_room" (
  "room_alias" TEXT PRIMARY KEY,
  "room_id" TEXT,
  "conference" TEXT
);

CREATE TABLE "direct_room" (
  "room_id" TEXT PRIMARY KEY,
  "matrix_user" TEXT,
  "xmpp_user" TEXT
);

CREATE TABLE "inviters" (
  "room_id" VARCHAR(255) PRIMARY KEY,
  "user_id" VARCHAR(255) NOT NULL
);
