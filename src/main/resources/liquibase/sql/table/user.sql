CREATE TABLE acmf.um_user (
  id         	SERIAL PRIMARY KEY,
  user_name     VARCHAR(50) NOT NULL,
  last_name  	VARCHAR(50) NOT NULL,
  first_name 	VARCHAR(50) NOT NULL,
  password 		VARCHAR(50) NOT NULL
);