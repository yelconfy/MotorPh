package DataAccess;

import Objects.enums.Status.LoanStatus;
import Objects.models.EmployeeLoan;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Employee_Loan and vw_LoanBalance (06 - Views).
 *
 * Schema (04 - Leave & Compensation Tables):
 *   Employee_Loan: LoanID, EmployeeID, DeductionTypeID, PrincipalAmount,
 *                  InterestRate, TotalPayable, InstallmentAmount, NumberOfTerms,
 *                  StartDate, Status, LastUpdatedBy, LastUpdatedDate
 *
 * Balance is derived from vw_LoanBalance (TotalPayable − paid Payroll_Deductions).
 * There is no stored balance column — always use GetOutstandingBalance().
 */
public class LoanDAO {

    /**
     * Insert — creates a new loan record. Returns generated LoanID.
     */
    public long Insert(Connection conn, EmployeeLoan loan, String createdBy)
            throws SQLException {

        String sql =
            "INSERT INTO Employee_Loan " +
            "(EmployeeID, DeductionTypeID, PrincipalAmount, InterestRate, " +
            " TotalPayable, InstallmentAmount, NumberOfTerms, StartDate, LastUpdatedBy) " +
            "OUTPUT INSERTED.LoanID " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, loan.GetEmployeeId());
            pstmt.setInt(2, loan.GetDeductionTypeId());
            pstmt.setDouble(3, loan.GetPrincipalAmount());

            if (loan.GetInterestRate() > 0) {
                pstmt.setDouble(4, loan.GetInterestRate());
            } else {
                pstmt.setNull(4, Types.DECIMAL);
            }

            pstmt.setDouble(5, loan.GetTotalPayable());
            pstmt.setDouble(6, loan.GetInstallmentAmount());
            pstmt.setInt(7, loan.GetNumberOfTerms());
            pstmt.setDate(8, Date.valueOf(loan.GetStartDate()));
            pstmt.setString(9, createdBy);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /**
     * UpdateStatus — marks a loan as FullyPaid or Cancelled.
     */
    public boolean UpdateStatus(Connection conn, long loanId,
                                LoanStatus newStatus, String updatedBy)
            throws SQLException {

        String sql =
            "UPDATE Employee_Loan SET Status = ?, LastUpdatedBy = ?, " +
            "LastUpdatedDate = SYSDATETIME() WHERE LoanID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newStatus.getValue());
            pstmt.setString(2, updatedBy);
            pstmt.setLong(3, loanId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * GetByEmployee — all loans for an employee (all statuses), newest first.
     */
    public List<EmployeeLoan> GetByEmployee(Connection conn, long employeeId)
            throws SQLException {

        List<EmployeeLoan> list = new ArrayList<>();
        String sql =
            "SELECT el.*, dt.DeductionName " +
            "FROM Employee_Loan el " +
            "JOIN Deduction_Type dt ON dt.DeductionTypeID = el.DeductionTypeID " +
            "WHERE el.EmployeeID = ? " +
            "ORDER BY el.StartDate DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, employeeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(new EmployeeLoan(rs));
            }
        }
        return list;
    }

    /**
     * GetActiveLoans — active loans only, used when building payroll deductions.
     */
    public List<EmployeeLoan> GetActiveLoans(Connection conn, long employeeId)
            throws SQLException {

        List<EmployeeLoan> list = new ArrayList<>();
        String sql =
            "SELECT el.*, dt.DeductionName " +
            "FROM Employee_Loan el " +
            "JOIN Deduction_Type dt ON dt.DeductionTypeID = el.DeductionTypeID " +
            "WHERE el.EmployeeID = ? AND el.Status = 0 " +  // 0 = Active
            "ORDER BY el.StartDate ASC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, employeeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(new EmployeeLoan(rs));
            }
        }
        return list;
    }

    /**
     * GetOutstandingBalance — remaining balance for a specific loan
     * by querying vw_LoanBalance.
     */
    public double GetOutstandingBalance(Connection conn, long loanId)
            throws SQLException {

        String sql = "SELECT OutstandingBalance FROM vw_LoanBalance WHERE LoanID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, loanId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble("OutstandingBalance") : 0.0;
            }
        }
    }
}