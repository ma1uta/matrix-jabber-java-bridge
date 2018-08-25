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

CREATE TABLE "inviters" (
  "room_id" VARCHAR(255) NOT NULL,
  "user_id" VARCHAR(255) NOT NULL,
  PRIMARY KEY ("room_id"),
);
