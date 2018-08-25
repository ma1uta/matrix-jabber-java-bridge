CREATE TABLE "app_user" (
  "localpart" VARCHAR(255) PRIMARY KEY
);

CREATE TABLE "transaction" (
  "id" VARCHAR(255) PRIMARY KEY,
  "processed" TIMESTAMP
);

CREATE TABLE "room_alias" (
  "alias" VARCHAR(255) PRIMARY KEY,
  "room_id" VARCHAR(255),
  "conference_jid" VARCHAR(255)
);

CREATE TABLE "bot_config" (
  "user_id" VARCHAR(255) PRIMARY KEY,
  "device_id" VARCHAR(255),
  "display_name" VARCHAR(255),
  "state" VARCHAR(255),
  "filter_id" VARCHAR(255)
);

CREATE TABLE "inviters" (
  "master_id" VARCHAR(255) NOT NULL,
  "room_id" VARCHAR(255) NOT NULL,
  "user_id" VARCHAR(255) NOT NULL,
  PRIMARY KEY ("master_id", "room_id"),
  CONSTRAINT FOREIGN KEY ("master_id") REFERENCES "bot_config" (user_id)
);
