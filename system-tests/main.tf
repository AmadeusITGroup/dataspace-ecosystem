locals {
  participants = [var.provider_name, var.consumer_name]
}


###################
## POSTGRESQL DB ##
###################

module "postgres" {
  source = "./modules/postgres"
}

######################
## PROVIDER BACKEND ##
######################

module "backend-provider" {
  source      = "./modules/backend-service"
  name        = var.provider_name
  environment = var.environment
}

##################
## PARTICIPANTS ##
##################

module "participant" {
  source = "./modules/participant"

  for_each                               = { for p in local.participants : p => p }
  participant_name                       = each.key
  db_server_fqdn                         = module.postgres.postgres_server_fqdn
  postgres_admin_credentials_secret_name = module.postgres.postgres_admin_credentials_secret_name
  environment                            = var.environment
}
#
###############
## AUTHORITY ##
###############

module "authority" {
  source = "./modules/authority"

  authority_name                         = var.authority_name
  db_server_fqdn                         = module.postgres.postgres_server_fqdn
  postgres_admin_credentials_secret_name = module.postgres.postgres_admin_credentials_secret_name
  environment                            = var.environment
}

