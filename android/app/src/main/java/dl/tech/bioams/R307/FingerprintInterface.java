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
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;

import dl.tech.bioams.R;
import dl.tech.bioams.models.Procedure;
import dl.tech.bioams.models.ProcedureCallback;

public class FingerprintInterface {
    public void enroll(String userId,Context context,Handler callbackHandler) throws IOException {
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
        ProcedureThread thread = new ProcedureThread(enrollProcedure,callbackHandler,context);
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
    ProcedureThread(Procedure procedure, Handler h, Context context) throws IOException {
        this.procedure = procedure;
        this.handler = h;
        fingerprint = new Fingerprint();
        fingerprint.init(context);
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
            byte subProcess = 0;
            Bundle bundle = new Bundle();
            Message message = new Message();
            switch (procedure.currentSubProcedure){
                case 0:
                case 3:
                    subProcess = FINGERPRINT_READIMAGE;
                    log("Scanning....");
                    fingerprint.readImage(this);
                    break;
                case 1:
                    subProcess = FINGERPRINT_CONVERTIMAGE;
                    log("Conveting image...");
                    fingerprint.convertImage(FINGERPRINT_CHARBUFFER1,this);
                    break;
                case 2:
                    subProcess = FINGERPRINT_SEARCHTEMPLATE;
                    log("Searching template....");
                    fingerprint.searchTemplate(FINGERPRINT_CHARBUFFER1,0,-1,this);
                    break;
                case 4:
                    subProcess = FINGERPRINT_CONVERTIMAGE;
                    fingerprint.convertImage(FINGERPRINT_CHARBUFFER2,this);
                    break;
                case 5:
                    subProcess = FINGERPRINT_COMPARECHARACTERISTICS;
                    fingerprint.compareCharacteristics(this);
                    break;
                case 6:
                    subProcess = FINGERPRINT_CREATETEMPLATE;
                    fingerprint.createTemplate(this);
                    break;
                case 7:
                    subProcess = FINGERPRINT_STORETEMPLATE;
                    fingerprint.storeTemplate(-1,FINGERPRINT_CHARBUFFER1,this);
            }
            bundle.putByte("subprocess",subProcess);
            message.setData(bundle);
            handler.sendMessage(message);
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
