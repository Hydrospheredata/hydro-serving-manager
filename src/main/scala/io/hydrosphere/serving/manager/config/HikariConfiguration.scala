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
    def toConfig(config: HikariConfiguration): HikariConfig = {
        val hikariConfig = new HikariConfig()
        hikariConfig.setJdbcUrl(config.jdbcUrl)
        hikariConfig.setUsername(config.username)
        hikariConfig.setPassword(config.password)
        hikariConfig.setDriverClassName(config.driverClassname)
        hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
        hikariConfig.setInitializationFailTimeout(-1)
        hikariConfig
    }
}