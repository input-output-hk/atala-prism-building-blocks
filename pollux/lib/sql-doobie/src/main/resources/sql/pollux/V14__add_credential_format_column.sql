ALTER TABLE public.issue_credential_records
    ADD COLUMN "credential_format" VARCHAR(9) DEFAULT "JWT" NOT NULL;