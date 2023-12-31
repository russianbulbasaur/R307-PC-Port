package dl.tech.bioams.api;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONObject;

public class CustomVolleyRequest extends Request {
    private final String url;
    private Response.Listener<Bundle> listener;
    private CustomVolleyError errorListener;
    public CustomVolleyRequest(int method, String url, CustomVolleyError errorListener,Response.Listener<Bundle> responseListen) {
        super(method, url, errorListener);
        this.url = url;
        this.listener = responseListen;
        this.errorListener = errorListener;
    }

    @Override
    protected Response parseNetworkResponse(NetworkResponse response) {
        String data = new String(response.data);
        Bundle bundle = new Bundle();
        bundle.putString("url",url);
        bundle.putString("response",data);
        return Response.success(bundle,null);
    }


    @Override
    protected void deliverResponse(Object response) {
        listener.onResponse((Bundle) response);
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }
}


