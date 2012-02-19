-- Creates the Toranomaki database for the EDICT dictionary.
-- Does not create any index.

CREATE TABLE entries
(
  ent_seq INTEGER NOT NULL,
  keb     VARCHAR(37),
  reb     VARCHAR(50),
  ke_pri  SMALLINT,
  re_pri  SMALLINT
);

CREATE TABLE information
(
  ent_seq     INTEGER     NOT NULL,
  element     VARCHAR(13) NOT NULL,
  description VARCHAR(64) NOT NULL
);

CREATE TABLE pos
(
  id          SMALLINT    NOT NULL,
  description VARCHAR(64) NOT NULL
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
  ent_seq INTEGER      NOT NULL,
  pos     SMALLINT     NOT NULL,
  lang    CHAR(3)      NOT NULL,
  gloss   VARCHAR(464) NOT NULL
);

CREATE TABLE xref
(
  ent_seq INTEGER NOT NULL,
  xref    INTEGER NOT NULL,
  ant     BOOLEAN NOT NULL
);
