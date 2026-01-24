DO
$$
BEGIN
  IF NOT EXISTS(
      SELECT
      FROM pg_user
      WHERE usename = 'acmfdevadmin')
  THEN
    CREATE USER acmfdevadmin
    WITH PASSWORD 'rand@mP@ssw0rd';
  END IF;
  CREATE SCHEMA IF NOT EXISTS acmf;
  ALTER SCHEMA acmf
  OWNER TO acmfdevadmin;
  GRANT ALL PRIVILEGES ON SCHEMA acmf TO acmfdevadmin;
  GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA acmf TO acmfdevadmin;
  GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA acmf TO acmfdevadmin;
END
$$;