-- Creates the Toranomaki database for the EDICT dictionary.
-- Does not create any index.

CREATE TABLE entries
(
  ent_seq INTEGER NOT NULL,
  keb     VARCHAR,
  reb     VARCHAR,
  ke_pri  SMALLINT,
  re_pri  SMALLINT
);

CREATE TABLE information
(
  ent_seq     INTEGER NOT NULL,
  element     VARCHAR NOT NULL,
  description VARCHAR NOT NULL
);

CREATE TABLE pos
(
  id          SMALLINT NOT NULL,
  description VARCHAR NOT NULL
);

CREATE TABLE priorities
(
  id   SMALLINT NOT NULL,
  news SMALLINT,
  ichi SMALLINT,
  spec SMALLINT,
  gai  SMALLINT,
  nf   SMALLINT
);

CREATE TABLE senses
(
  ent_seq INTEGER  NOT NULL,
  pos     SMALLINT NOT NULL,
  lang    CHAR(3)  NOT NULL,
  gloss   VARCHAR  NOT NULL
);

CREATE TABLE xref
(
  ent_seq INTEGER NOT NULL,
  xref    INTEGER NOT NULL,
  ant     BOOLEAN NOT NULL
);
