-- Creates the database index.
-- This script shall be run only after the database has been fully populated.

ALTER TABLE pos        ADD CONSTRAINT pos_pkey        PRIMARY KEY (id);
ALTER TABLE priorities ADD CONSTRAINT priorities_pkey PRIMARY KEY (id);
ALTER TABLE xref       ADD CONSTRAINT references_pkey PRIMARY KEY (ent_seq, xref);

CREATE INDEX entries_keb_idx     ON entries     (keb);
CREATE INDEX entries_reb_idx     ON entries     (reb);
CREATE INDEX entries_seq_idx     ON entries     (ent_seq);
CREATE INDEX information_seq_idx ON information (ent_seq);
CREATE INDEX senses_seq_idx      ON senses      (ent_seq);
CREATE INDEX senses_gloss_idx    ON senses      (gloss);

ALTER TABLE entries ADD CONSTRAINT entries_ke_pri_fkey FOREIGN KEY (ke_pri) REFERENCES priorities (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE entries ADD CONSTRAINT entries_re_pri_fkey FOREIGN KEY (re_pri) REFERENCES priorities (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE senses  ADD CONSTRAINT senses_pos_fkey     FOREIGN KEY (pos)    REFERENCES pos        (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
