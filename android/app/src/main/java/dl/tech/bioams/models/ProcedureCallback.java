package dl.tech.bioams.models;

import java.io.IOException;
import java.util.HashMap;

public interface ProcedureCallback {
    void subProcedureFinished(HashMap<String,Object> data) ;
}

