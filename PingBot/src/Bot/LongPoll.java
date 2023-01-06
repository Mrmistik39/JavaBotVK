package Bot;

import com.google.gson.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LongPoll {

    public JsonObject ts;
    public static String server;
    public static int wait = 25;

    public static void main(String[] args) {
        new LongPoll().run();
    }

    public void run(){
        this.getSession();
        this.LongPolling();
    }

    public static String TOKEN = "токен группы вк";
    public static double VER = 5.132;
    public static int GROUP_ID = 0; // id группы вк

    public void getSession(){
        System.out.println("Получение сессии...");
        String pk = "https://api.vk.com/method/groups.getLongPollServer?group_id=" + GROUP_ID + "&access_token=" + TOKEN + "&v=" + VER;
        try {
            this.ts = (JsonObject) new JsonParser().parse(this.get(pk));
            System.out.println("Сессия успешно получена");
        }catch (Exception e){
            System.out.println("Ошибка получения сессии -> " + e.getMessage());
        }
    }

    public String get(String url){
        String line;
        StringBuilder result = new StringBuilder();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            while ((line = r.readLine()) != null)
                result.append(line);
            r.close();
        } catch (Exception e){
            System.err.println("Error get request, error: " + e);
            return get(url);
        }
        return result.toString();
    }


    public JsonElement Updates(){
        try {
            JsonElement response = this.ts.getAsJsonObject().get("response");
            String ts = response.getAsJsonObject().get("ts").getAsString();
            server = response.getAsJsonObject().get("server").getAsString();
            String key = response.getAsJsonObject().get("key").getAsString();
            String pk = String.format("%s?act=a_check&key=%s&wait=" + wait + "&ts=%s", server, key, ts);
            return new JsonParser().parse(this.get(pk));
        }catch (Exception e){
            System.err.println("Session json parse error: " + e);
        }
        return null;
    }

    public JsonElement handlerFailed(JsonElement jsonObject){
        if(jsonObject.getAsJsonObject().get("failed") != null){
            String error;
            int failedCode = jsonObject.getAsJsonObject().get("failed").getAsInt();
            switch (failedCode){
                case 1 -> {
                    int ts = jsonObject.getAsJsonObject().get("ts").getAsInt();
                    error = "История событий устарела или была частично утеряна. Новый ts:" + ts;
                    this.ts.getAsJsonObject().get("response").getAsJsonObject().addProperty("ts", ts);
                }
                case 2 -> {
                    error = "Истекло время действия ключа";
                    this.getSession();
                }
                case 3 -> {
                    error = "Информация утрачена";
                    this.getSession();
                }
                default -> {
                    error = "Неизвестная ошибка: " + failedCode;
                    this.getSession();
                }
            }
            System.err.println("Ошибка получения сессии: " + error);
            return this.Updates();
        }else{
            return jsonObject;
        }
    }

    public void LongPolling(){
        while(true) {
            JsonElement updates = this.Updates();
            long ts;
            updates = this.handlerFailed(updates);
            ts = updates.getAsJsonObject().get("ts").getAsLong();
            for (int i = 0; i != updates.getAsJsonObject().get("updates").getAsJsonArray().size(); ++i) {
                JsonElement update = updates.getAsJsonObject().get("updates").getAsJsonArray().get(i);
                this.ts.getAsJsonObject().get("response").getAsJsonObject().addProperty("ts", ts);
                String event_type = update.getAsJsonObject().get("type").getAsString();
                System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(update));
                switch (event_type) {
                    case "message_new" -> {
                        JsonObject u = update.getAsJsonObject().get("object").getAsJsonObject();
                        JsonObject object;
                        if(u.get("message") != null)
                            object = u.get("message").getAsJsonObject();
                        else
                            object = u;
                        String[] text = object.get("text").getAsString().split(" ");
                        if(text.length > 0){
                            switch (text[0].toLowerCase()){
                                case "ping" -> {
                                    int peer_id = object.get("peer_id").getAsInt();
                                    long date = object.getAsJsonObject().get("date").getAsLong();
                                    long serverTime = new JsonParser().parse(this.get("https://api.vk.com/method/utils.getServerTime?access_token=" + TOKEN + "&v=5.103")).getAsJsonObject().get("response").getAsLong();
                                    this.get("https://api.vk.com/method/messages.send?v=5.103&access_token=" + TOKEN + "&random_id=0&peer_id=" + peer_id + "&message=" + urlEncoder("Ping: " + (serverTime - date) + "s"));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static String urlEncoder(String text){
        return text
                .replace(" ", "%20")
                .replace("\n", "%0A")
                .replace("#", URLEncoder.encode("#", StandardCharsets.UTF_8))
                .replace("&", URLEncoder.encode("&", StandardCharsets.UTF_8));
    }
}
