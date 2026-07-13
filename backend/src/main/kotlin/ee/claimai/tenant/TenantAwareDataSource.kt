package ee.claimai.tenant

import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

@Component
class TenantSchemaBeanPostProcessor : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is DataSource && beanName != "migrationDataSource") {
            return TenantAwareDataSource(bean)
        }
        return bean
    }
}

class TenantAwareDataSource(private val target: DataSource) : DataSource {

    override fun getConnection(): Connection {
        val conn = target.connection
        applySearchPath(conn)
        return conn
    }

    override fun getConnection(username: String, password: String): Connection {
        val conn = target.getConnection(username, password)
        applySearchPath(conn)
        return conn
    }

    override fun getLoginTimeout(): Int = target.loginTimeout
    override fun setLoginTimeout(seconds: Int) { target.loginTimeout = seconds }
    override fun getLogWriter(): PrintWriter = target.logWriter
    override fun setLogWriter(out: PrintWriter) { target.logWriter = out }
    override fun getParentLogger(): Logger = target.parentLogger

    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) return iface.cast(this)
        return target.unwrap(iface)
    }

    override fun isWrapperFor(iface: Class<*>): Boolean =
        iface.isInstance(this) || target.isWrapperFor(iface)

    private fun applySearchPath(conn: Connection) {
        val tenant = TenantContext.get()
        val searchPath = if (tenant != null) "$tenant, public" else "public"
        conn.prepareStatement("SELECT set_config('search_path', ?, false)").use { stmt ->
            stmt.setString(1, searchPath)
            stmt.execute()
        }
    }
}
