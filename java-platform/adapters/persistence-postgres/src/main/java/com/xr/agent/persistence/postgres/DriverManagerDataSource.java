package com.xr.agent.persistence.postgres;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Minimal JDBC data source for deployments that do not provide a connection pool.
 */
public final class DriverManagerDataSource implements DataSource {

    private final String url;
    private final String user;
    private final String password;

    public DriverManagerDataSource(String url, String user, String password) {
        this.url = Objects.requireNonNull(url, "url");
        this.user = Objects.requireNonNull(user, "user");
        this.password = Objects.requireNonNull(password, "password");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public Connection getConnection(String username, String configuredPassword) throws SQLException {
        return DriverManager.getConnection(url, username, configuredPassword);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        DriverManager.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("DriverManager does not expose a parent logger");
    }

    @Override
    public <T> T unwrap(Class<T> interfaceType) throws SQLException {
        if (isWrapperFor(interfaceType)) {
            return interfaceType.cast(this);
        }
        throw new SQLException("DataSource is not a wrapper for " + interfaceType.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> interfaceType) {
        return interfaceType != null && interfaceType.isInstance(this);
    }
}
