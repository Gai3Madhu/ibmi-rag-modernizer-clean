**FREE
// CUSTMST - Customer Master File
// File: CUSTMST - Customer Master

Dcl-F CUSTMST Keyed Usage(*Update);

Dcl-Ds CustRec;
  CustId Packed(7:0);
  CustName Char(30);
  CustBal Packed(7:2); // Balance - MONEY field, 2 decimals
  CustStatus Char(1); // A=Active, I=Inactive
End-Ds;

Dcl-Proc GetCustomer;
  Chain CustId CUSTMST;
  If %Found(CUSTMST);
    // Customer found, return balance
    Return CustBal;
  Else;
    // Customer not found
    Return 0;
  EndIf;
End-Proc;

Dcl-Proc UpdateBalance;
  Chain CustId CUSTMST;
  If %Found(CUSTMST);
    CustBal = CustBal + 100.50;
    Update CUSTMST;
    // IMPORTANT: Need COMMIT here in Java conversion
  EndIf;
End-Proc;s