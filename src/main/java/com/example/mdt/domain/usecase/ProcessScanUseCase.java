package com.example.mdt.domain.usecase;
import com.example.mdt.domain.gateway.DeteccionesGateway;
import com.example.mdt.domain.model.Deteccion;
import com.example.mdt.domain.model.Scan;
import java.time.LocalDateTime;
public class ProcessScanUseCase {
    private final DeteccionesGateway gateway;
    public ProcessScanUseCase(DeteccionesGateway gateway){ this.gateway = gateway; }
    public int process(Scan scan){
        Long ubicacionId = parseLongOrNull(scan.stage());
        Long lectorId = parseLectorId(scan.device());
        int count = 0;
        for (String csn : scan.csn()){
            if (csn == null || csn.length() < 2) continue;
            Integer rssi = parseRssiFromCsn(csn);
            var det = new Deteccion(lectorId, ubicacionId, csn, rssi, scan.machine(), LocalDateTime.now());
            gateway.save(det);
            count++;
        }
        return count;
    }
    private static Long parseLongOrNull(String s){
        if (s == null) return null;
        try { return Long.parseLong(s.replaceAll("[^0-9]", "")); } catch(Exception e){ return null; }
    }
    private static Long parseLectorId(String device){
        if (device == null) return null;
        try {
            String leading = device.replaceAll("^(\\d+).*$", "$1");
            if (leading.matches("\\d+")) return Long.parseLong(leading);
            String all = device.replaceAll("[^0-9]", "");
            if (!all.isEmpty()) return Long.parseLong(all);
        } catch(Exception ignored){}
        return null;
    }
    public static Integer parseRssiFromCsn(String csn){
        if (csn == null || csn.length() < 2) return null;
        String last2 = csn.substring(csn.length()-2);
        try { return Integer.parseInt(last2, 16); } catch(Exception e){ return null; }
    }
}
