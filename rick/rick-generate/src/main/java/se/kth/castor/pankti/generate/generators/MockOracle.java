package se.kth.castor.pankti.generate.generators;

public enum MockOracle {
    OO,
    PO,
    CO;

    public String getFullName(MockOracle oracleEnum) {
        String oracleName;
        if (oracleEnum.toString().equals("OO"))
            oracleName = "output";
        else if (oracleEnum.toString().equals("PO"))
            oracleName = "parameter";
        else oracleName = "call";
        return oracleName + " oracle";
    }
}
