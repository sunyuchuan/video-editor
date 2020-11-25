package tv.danmaku.ijk.media.example.widget.srtview;

/**
 * Created by 王松 on 2016/10/20.
 */

public class SrtBean {
    private String role;
    private String text;
    private long start;
    private long end;

    public SrtBean() {
    }

    public SrtBean(String text, long start, long end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }
}
