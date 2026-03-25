package com.example.smartschoolfinder.data;

import com.example.smartschoolfinder.model.School;

public class MockTransportProvider {
    public static String buildTransportText(School school) {
        return "Bus: " + school.getTransportBus() + "\n"
                + "Minibus: " + school.getTransportMinibus() + "\n"
                + "MTR: " + school.getTransportMtr() + "\n"
                + "Convenience: " + school.getTransportConvenience();
    }
}
