package tv.danmaku.ijk.media.example.widget.srtview;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SrtUtil {
    /**
     * 传入的参数为标准歌词字符串
     * @param srtStr
     * @return
     */
    public static List<SrtBean> parseStr2List(String srtStr) {
        List<SrtBean> list = new ArrayList<>();
        String lrcText = srtStr.replaceAll("&#58;", ":")
                .replaceAll("&#10;", "\n")
                .replaceAll("&#46;", ".")
                .replaceAll("&#32;", " ")
                .replaceAll("&#45;", "-")
                .replaceAll("&#13;", "\r").replaceAll("&#39;", "'");
        String[] split = lrcText.split("\n");
        for (int i = 0; i < split.length;) {
            if (split[i].equals("")) {
                //this is blank line
                i++;
                continue;
            }
            String num = split[i];
            String time = split[i + 1];
            String text = split[i + 2];

            //Log.d("srtparser", time);
            SrtBean srtBean = new SrtBean();
            String startTime = time.substring(0, time.indexOf("-->") - 1);
            String endTime = time.substring(time.indexOf("-->") + "-->".length() + 1, time.length());
            srtBean.setStart(convertTime(startTime));
            srtBean.setEnd(convertTime(endTime));
            //Log.d("srtparser", " startTime " + convertTime(startTime) + " endTime " + convertTime(endTime));
            //find the first 》
            if (text.indexOf("》") < 0) {
                //no role
                srtBean.setText(text);
            } else {
                srtBean.setRole(text.substring(0, text.indexOf("》") - 1));
                srtBean.setText(text.substring(text.indexOf("》") + 1, text.length()));
            }
            list.add(srtBean);
            i += 3;
        }
        return list;
    }

    private static long convertTime(String time) {
        String[] split = time.split(":");
        String hour = split[0];
        String min = split[1];

        String[] split1 = split[2].split(",");
        String second = split1[0];
        String mills = split1[1];

        //Log.d("srtparser", " hour " + hour + " min " + min + " second " + second + " mills " + mills);
        return Long.valueOf(hour) * 60 * 60 * 1000 + Long.valueOf(min) * 60 * 1000 + Long.valueOf(second) * 1000 + Long.valueOf(mills);
    }

    private static void readToBuffer(StringBuffer buffer, String filePath) throws IOException {
        InputStream is = new FileInputStream(filePath);
        String line; // 用来保存每行读取的内容
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        line = reader.readLine(); // 读取第一行
        while (line != null) { // 如果 line 为空说明读完了
            buffer.append(line); // 将读到的内容添加到 buffer 中
            buffer.append("\n"); // 添加换行符
            line = reader.readLine(); // 读取下一行
        }
        reader.close();
        is.close();
    }

    /**
     * 读取文本文件内容
     * @param filePath 文件所在路径
     * @return 文本内容
     * @throws IOException 异常
     * @author cn.outofmemory
     * @date 2013-1-7
     */
    public static String readFile(String filePath) throws IOException {
        StringBuffer sb = new StringBuffer();
        readToBuffer(sb, filePath);
        return sb.toString();
    }
}
