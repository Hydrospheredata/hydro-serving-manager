package io.hydrosphere.serving.manager.config

import com.zaxxer.hikari.HikariConfig

case class HikariConfiguration(
    jdbcUrl: String,
    username: String,
    password: String,
    driverClassname: String = "org.postgresql.Driver",
    maximumPoolSize: Int,
    initializationFailTimeout: Long = 20000L,
    leakDetectionThreshold: Long = 5000L,
    threadPoolSize: Int = 32
)

object HikariConfiguration {
  def toConfig(config: HikariConfiguration): HikariConfig = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setDriverClassName(config.driverClassname)
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setInitializationFailTimeout(config.initializationFailTimeout)
    hikariConfig.setLeakDetectionThreshold(config.leakDetectionThreshold)
    hikariConfig
  }
}
