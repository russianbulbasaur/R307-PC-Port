package dl.tech.bioams.api;

import android.os.Bundle;

import com.android.volley.Response;
import com.android.volley.VolleyError;

public class CustomVolleyError implements Response.ErrorListener {
    private String url;
    public CustomVolleyInterface volleyInterface;

    public CustomVolleyError(String url){
        this.url = url;
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        Bundle bundle = new Bundle();
        bundle.putString("error",error.getMessage().toString());
        bundle.putString("url",getUrl());
    }

    public String getUrl(){
        return url;
    }
}