package com.gl.examination;

import org.junit.jupiter.api.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatabaseQueryTest {

    private static Connection connection;

    @BeforeAll
    public static void init() throws Exception {
        connection = DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/ecommerce_db", "root", ""
        );
    }

    @AfterAll
    public static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    @Order(1)
    public void testTableStructure() throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet rs = metaData.getColumns("ecommerce_db", null, "orders", null);

        int count = 0;
        boolean hasOrderId = false, hasCustomerId = false, hasStatus = false;

        while (rs.next()) {
            count++;
            String column = rs.getString("COLUMN_NAME").toLowerCase();
            switch (column) {
                case "order_id": hasOrderId = true; break;
                case "customer_id": hasCustomerId = true; break;
                case "status": hasStatus = true; break;
            }
        }

        assertTrue(hasOrderId, "'order_id' column missing");
        assertTrue(hasCustomerId, "'customer_id' column missing");
        assertTrue(hasStatus, "'status' column missing");
        assertEquals(3, count, "Table should have exactly 3 columns");
    }

    @Test
    @Order(2)
    public void testSampleDataInserted() throws Exception {
        PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM orders");
        ResultSet rs = ps.executeQuery();
        rs.next();
        int rowCount = rs.getInt(1);
        assertEquals(6, rowCount, "Expected 6 rows in 'orders' table");
    }

    @Test
    @Order(3)
    public void testProcedureExists() throws Exception {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT ROUTINE_NAME FROM information_schema.ROUTINES " +
            "WHERE ROUTINE_SCHEMA = 'ecommerce_db' AND ROUTINE_TYPE = 'PROCEDURE' " +
            "AND ROUTINE_NAME = 'UpdateOrderStatus'"
        );
        ResultSet rs = ps.executeQuery();
        assertTrue(rs.next(), "Procedure 'UpdateOrderStatus' should exist");
    }

    @Test
    @Order(4)
    public void testProcedureLogic_UpdatesStatus() throws Exception {
        // Reset order_id = 1 back to 'Processing'
        PreparedStatement resetPs = connection.prepareStatement(
            "UPDATE orders SET status = 'Processing' WHERE order_id = 1"
        );
        resetPs.executeUpdate();

        // Call the procedure
        CallableStatement cs = connection.prepareCall("{CALL UpdateOrderStatus(?)}");
        cs.setInt(1, 1);
        ResultSet rs = cs.executeQuery();
        assertTrue(rs.next());
        assertEquals("Order 1 status updated to Shipped!", rs.getString("message"));

        // Verify DB change
        PreparedStatement ps = connection.prepareStatement(
            "SELECT status FROM orders WHERE order_id = 1"
        );
        ResultSet rs2 = ps.executeQuery();
        rs2.next();
        assertEquals("Shipped", rs2.getString("status"));
    }


    @Test
    @Order(5)
    public void testProcedureLogic_AlreadyShipped() throws Exception {
        // Order 2 already Shipped
        CallableStatement cs = connection.prepareCall("{CALL UpdateOrderStatus(?)}");
        cs.setInt(1, 2);
        ResultSet rs = cs.executeQuery();
        assertTrue(rs.next());
        assertEquals("Order 2 has already been shipped.", rs.getString("message"));
    }
}
