package io.hydrosphere.serving.manager.config

import com.zaxxer.hikari.HikariConfig

case class HikariConfiguration(
  jdbcUrl: String,
  username: String,
  password: String,
  driverClassname: String = "org.postgresql.Driver",
  maximumPoolSize: Int
)

object HikariConfiguration {
    def toConfig(hikariConfiguration: HikariConfiguration): HikariConfig = {
        val hikariConfig = new HikariConfig()
        hikariConfig.setJdbcUrl(hikariConfiguration.jdbcUrl)
        hikariConfig.setUsername(hikariConfiguration.username)
        hikariConfig.setPassword(hikariConfiguration.password)
        hikariConfig.setDriverClassName(hikariConfiguration.driverClassname)
        hikariConfig.setMaximumPoolSize(hikariConfiguration.maximumPoolSize)
        hikariConfig.setInitializationFailTimeout(20000)
        hikariConfig
    }
}