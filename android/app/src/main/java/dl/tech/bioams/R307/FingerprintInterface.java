package dl.tech.bioams.R307;

import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER1;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CHARBUFFER2;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_COMPARECHARACTERISTICS;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CONVERTIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_CREATETEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_READIMAGE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_SEARCHTEMPLATE;
import static dl.tech.bioams.R307.DataCodes.FINGERPRINT_STORETEMPLATE;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;

import dl.tech.bioams.models.Procedure;
import dl.tech.bioams.models.ProcedureCallback;

public class FingerprintInterface {
    public void enroll(String userId,Activity activity,Handler callbackHandler) throws IOException {
        Procedure enrollProcedure = new Procedure();
        enrollProcedure.currentSubProcedure = 0;
        enrollProcedure.subProcedures = new byte[]{
                FINGERPRINT_READIMAGE,
                FINGERPRINT_CONVERTIMAGE,
                FINGERPRINT_SEARCHTEMPLATE,
                FINGERPRINT_READIMAGE,
                FINGERPRINT_CONVERTIMAGE,
                FINGERPRINT_COMPARECHARACTERISTICS,
                FINGERPRINT_CREATETEMPLATE,
                FINGERPRINT_STORETEMPLATE
        };
        enrollProcedure.name = "enroll";
        //Thread
        ProcedureThread thread = new ProcedureThread(enrollProcedure,callbackHandler,activity);
        thread.start();
    }

    public void search(Activity activity,Handler callbackHandler) throws IOException {
        Procedure enrollProcedure = new Procedure();
        enrollProcedure.currentSubProcedure = 0;
        enrollProcedure.subProcedures = new byte[]{
                FINGERPRINT_READIMAGE,
                FINGERPRINT_CONVERTIMAGE,
                FINGERPRINT_SEARCHTEMPLATE
        };
        enrollProcedure.name = "search";
        //Thread
        ProcedureThread thread = new ProcedureThread(enrollProcedure,callbackHandler,activity);
        thread.start();
    }
}


class ProcedureThread extends Thread implements ProcedureCallback {
    Procedure procedure;
    Handler handler;
    Fingerprint fingerprint;
    ProcedureThread(Procedure procedure, Handler h, Activity activity) throws IOException {
        this.procedure = procedure;
        this.handler = h;
        fingerprint = new Fingerprint();
        fingerprint.init(activity);
    }

    @Override
    public void run() {
        super.run();
        if(procedure.name.equals("enroll")) {
            try {
                //fingerprint.searchTemplate(FINGERPRINT_CHARBUFFER1,0,-1,this);
                next(new HashMap<>());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void next(HashMap<String,Object> data) throws IOException,InterruptedException{
        if(procedure.name.equals("enroll")){
            switch (procedure.currentSubProcedure){
                case 0:
                case 3:
                    log("Scanning....");
                    fingerprint.readImage(this);
                    break;
                case 1:
                    log("Conveting image...");
                    fingerprint.convertImage(FINGERPRINT_CHARBUFFER1,this);
                    break;
                case 2:
                    log("Searching template....");
                    fingerprint.searchTemplate(FINGERPRINT_CHARBUFFER1,0,-1,this);
                    break;
                case 4:
                    fingerprint.convertImage(FINGERPRINT_CHARBUFFER2,this);
                    break;
                case 5:
                    fingerprint.compareCharacteristics(this);
                    break;
                case 6:
                    fingerprint.createTemplate(this);
                    break;
                case 7:
                    fingerprint.storeTemplate(-1,FINGERPRINT_CHARBUFFER1,this);
            }
        }
    }

    @Override
    public void subProcedureFinished(HashMap<String, Object> data) {
        procedure.currentSubProcedure += 1;
        System.out.println(procedure.currentSubProcedure);
        try {
            next(data);
        } catch (IOException | InterruptedException e) {
            System.out.println("error");
            throw new RuntimeException(e);
        }
    }



    void log(String text){
        System.out.println(text);
    }
}
