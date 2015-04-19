package com.zackehh.floodz.util;

import com.zackehh.corba.common.Alert;
import com.zackehh.corba.common.MetaData;
import com.zackehh.corba.common.Reading;
import com.zackehh.corba.common.SensorMeta;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton instance used for persisting various data from inside the
 * Regional Monitoring Centre in order to survive restarts. Used to keep
 * track of any alerts received, and any connected Local Monitoring Stations.
 */
public class SQLiteClient {

    /**
     * The Connection to the local database.
     */
    private final Connection db;

    /**
     * The singleton instance of SQLiteClient.
     */
    private static SQLiteClient instance = null;

    /**
     * Creates a new SQLiteClient instance, and sets up the required tables
     * for the Regional Monitoring Centre to function correctly. The ctor
     * is private in order to provide singleton access.
     *
     * @throws SQLException
     */
    private SQLiteClient() throws SQLException {
        // load the sqlite driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Unable to load db driver!");
        }

        // create a sqlite connection
        db = DriverManager.getConnection("jdbc:sqlite:local.db");

        // table to store connected Local Monitoring Stations
        Statement createTableForLMS = db.createStatement();
        createTableForLMS.executeUpdate(
                "CREATE TABLE IF NOT EXISTS RMC " +
                "(ID    TEXT    PRIMARY KEY," +
                " NAME  TEXT    NOT NULL," +
                " LMS   TEXT    NOT NULL)"
        );
        createTableForLMS.closeOnCompletion();

        // table to store received Alerts coming from an LMS
        Statement createTableForAlerts = db.createStatement();
        createTableForAlerts.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ALERTS " +
                "(ID            INTEGER     PRIMARY KEY," +
                " LMS           TEXT        NOT NULL," +
                " TIME          DATE        NOT NULL," +
                " ZONE          CHAR(26)    NOT NULL," +
                " SENSOR        CHAR(4)     NOT NULL," +
                " MEASUREMENT   INT         NOT NULL," +
                " FOREIGN KEY(LMS) REFERENCES RMC(LMS))"
        );
        createTableForAlerts.closeOnCompletion();
    }

    /**
     * Singleton accessor method for use by other classes. This is
     * used to avoid multiple clients connecting to the local db and
     * causing any race conditions. All access to this class uses the
     * same instance.
     *
     * @return an SQLiteClient instance
     */
    public static SQLiteClient getInstance() {
        try {
            if (instance == null) {
                instance = new SQLiteClient();
            }
            return instance;
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes an Alert record from the local database. Technically this
     * could remove more than one, but realistically there should only ever
     * be one matching pattern.
     *
     * @param metadata the Alert metadata
     * @return true if removed successfully
     */
    public synchronized boolean deleteAlert(MetaData metadata) {
        try {
            // create a statement
            PreparedStatement deleteStatement = db.prepareStatement(
                "DELETE FROM ALERTS WHERE LMS=? AND ZONE=? AND SENSOR=?;"
            );

            // assign statement arguments
            deleteStatement.setString(1, metadata.lms);
            deleteStatement.setString(2, metadata.sensorMeta.zone);
            deleteStatement.setString(3, metadata.sensorMeta.sensor);

            // execute the update
            deleteStatement.executeUpdate();

            // block until completion
            deleteStatement.closeOnCompletion();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Removes an LMS record from the local database. This is called when
     * an LMS disconnects from an RMC (i.e. LMS termination).
     *
     * @param rmc the name of the RMC
     * @param lms the name of the LMS
     * @return true if successful
     */
    public synchronized boolean deleteLMS(String rmc, String lms) {
        try {
            // create a statement
            PreparedStatement deleteStatement = db.prepareStatement(
                "DELETE FROM RMC WHERE NAME=? AND LMS=?;"
            );

            // set the statement arguments
            deleteStatement.setString(1, rmc);
            deleteStatement.setString(2, lms);

            // execute the update
            deleteStatement.executeUpdate();

            // block until completion
            deleteStatement.closeOnCompletion();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Inserts a new Alert record into the database. Stores the following:
     *
     * - Name of the LMS it came via
     * - Time of the recorded alert
     * - Zone of the LMS the alert came from
     * - Sensor which reported the alert
     * - Measurement of the water level
     *
     * @param alert the initial Alert
     * @return true if successful
     */
    public synchronized boolean insertAlert(Alert alert) {
        try {
            // create a statement
            PreparedStatement insertStatement = db.prepareStatement(
                "INSERT INTO ALERTS (LMS,TIME,ZONE,SENSOR,MEASUREMENT)" +
                "VALUES (?,?,?,?,?);"
            );

            // set the statement arguments
            insertStatement.setString(1, alert.meta.lms);
            insertStatement.setLong(2, alert.reading.time);
            insertStatement.setString(3, alert.meta.sensorMeta.zone);
            insertStatement.setString(4, alert.meta.sensorMeta.sensor);
            insertStatement.setInt(5, alert.reading.measurement);

            // execute the update
            insertStatement.executeUpdate();

            // block until persisted
            insertStatement.closeOnCompletion();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Inserts a new LMS record into the database. Stores the following:
     *
     * - Name of the RMC
     * - LMS name
     *
     * @param rmc the name of the RMC
     * @param lms the name of the LMS
     * @return true if successful
     */
    public synchronized boolean insertLMS(String rmc, String lms) {
        try {
            // create a statement
            PreparedStatement insertStatement = db.prepareStatement(
                "INSERT INTO RMC (NAME,LMS)" +
                "VALUES (?,?);"
            );

            // set the statement arguments
            insertStatement.setString(1, rmc);
            insertStatement.setString(2, lms);

            // start the update
            insertStatement.executeUpdate();

            // block until completion
            insertStatement.closeOnCompletion();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Update an Alert record in the database. This occurs when a zone
     * already above the warning and it increases further.
     *
     * @param alert the Alert details
     * @return true on success
     */
    public synchronized boolean updateAlert(Alert alert) {
        try {
            // create a statement
            PreparedStatement updateStatement = db.prepareStatement(
                "UPDATE ALERTS " +
                "SET MEASUREMENT=? AND TIME=? " +
                "WHERE LMS=? AND ZONE=? AND SENSOR=?;"
            );

            // set the statement arguments
            updateStatement.setInt(1, alert.reading.measurement);
            updateStatement.setLong(2, alert.reading.time);
            updateStatement.setString(3, alert.meta.lms);
            updateStatement.setString(4, alert.meta.sensorMeta.zone);
            updateStatement.setString(5, alert.meta.sensorMeta.sensor);

            // execute the update
            updateStatement.executeUpdate();

            // block until completion
            updateStatement.closeOnCompletion();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Retrieve a list of all Alert entries in the database currently.
     * Orders the results by time, with the oldest appearing at the top -
     * the theory being that the oldest is the most urgent.
     *
     * @return a List<Alert> instance
     */
    public List<Alert> retrieveAllAlerts() {
        // initialize an empty List<Alert>
        List<Alert> alertLog = new ArrayList<>();

        try {
            // create a statement
            Statement alertQueryStatement = db.createStatement();

            // query the database for the Alerts
            ResultSet resultSet = alertQueryStatement.executeQuery(
                "SELECT * FROM ALERTS " +
                "ORDER BY TIME ASC;"
            );

            // for each row returned
            while (resultSet.next()) {
                // create a meta instance for the sensor/zone
                SensorMeta meta = new SensorMeta(resultSet.getString("ZONE"),
                        resultSet.getString("SENSOR"), 0);

                // add the Alert to the list
                alertLog.add(new Alert(
                        new MetaData(resultSet.getString("LMS"), meta),
                        new Reading(resultSet.getLong("TIME"), resultSet.getInt("MEASUREMENT"))
                ));
            }

            // close the result set
            resultSet.close();

            // close the statement
            alertQueryStatement.closeOnCompletion();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // return the list of alerts
        return alertLog;
    }

    /**
     * Retrieves a list of currently tracked Local Monitoring Stations. This is a
     * list of the station names in order to provide them for lookups later.
     *
     * @return a list of LMS names
     */
    public List<String> retrieveLocalNames() {
        // initialize an empty List<Alert>
        List<String> lmsNames = new ArrayList<>();

        try {
            // create a new statement
            Statement lmsNamesStatement = db.createStatement();

            // query the database for distinct LMS names
            ResultSet resultSet = lmsNamesStatement
                    .executeQuery("SELECT DISTINCT LMS FROM RMC;");

            // for each row returned
            while (resultSet.next()) {
                // add the name to the list
                lmsNames.add(resultSet.getString("LMS"));
            }

            // close the result set
            resultSet.close();

            // block until completion
            lmsNamesStatement.closeOnCompletion();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // return the list of names
        return lmsNames;
    }

}