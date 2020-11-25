package tv.danmaku.ijk.media.example.widget.media;

import android.util.Log;

import com.github.faucamp.simplertmp.Util;
import com.github.faucamp.simplertmp.amf.AmfNull;
import com.github.faucamp.simplertmp.amf.AmfNumber;
import com.github.faucamp.simplertmp.amf.AmfObject;
import com.github.faucamp.simplertmp.io.ChunkStreamInfo;
import com.github.faucamp.simplertmp.io.RtmpDecoder;
import com.github.faucamp.simplertmp.io.RtmpSessionInfo;
import com.github.faucamp.simplertmp.io.packets.Abort;
import com.github.faucamp.simplertmp.io.packets.Command;
import com.github.faucamp.simplertmp.io.packets.ContentData;
import com.github.faucamp.simplertmp.io.packets.Data;
import com.github.faucamp.simplertmp.io.packets.Handshake;
import com.github.faucamp.simplertmp.io.packets.RtmpHeader;
import com.github.faucamp.simplertmp.io.packets.RtmpPacket;
import com.github.faucamp.simplertmp.io.packets.UserControl;
import com.github.faucamp.simplertmp.io.packets.WindowAckSize;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * Created by jsyan on 16-12-8.
 */
public class SyncRtmpClient {
    private final static String TAG = "SyncRtmpClient";
    private String mAppName;
    private String mHost;
    private String mPlayPath;
    private String mTcUrl;
    private int mPort;
    private int transactionIdCounter = 0;
    private int mCurrentStreamId = -1;
    private Socket socket;
    private RtmpSessionInfo mRtmpSessionInfo;
    private static final int SOCKET_CONNECT_TIMEOUT_MS = 3000;
    private static final int SOCKET_READ_TIMEOUT_MS = 10000;
    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private RtmpDecoder mRtmpDecoder;
    private State mState = State.IDLE;
    private static final byte[] RESERVED = new byte[]{0, 0, 0, 0};

    private enum State {
        IDLE,
        HANDSHAKE,
        CONNECT,
        CREATESTREAM,
        PLAY
    }

    public SyncRtmpClient(String host, int port, String appName, String playpath) {
        this.mHost = host;
        this.mPort = port;
        this.mAppName = appName;
        this.mPlayPath = playpath;
        this.mTcUrl = "rtmp://" + host + ":" + port + "/" + appName;
        Log.d(TAG, mTcUrl);
        mRtmpSessionInfo = new RtmpSessionInfo();
        mRtmpDecoder = new RtmpDecoder(mRtmpSessionInfo);
    }

    //1.send handshake
    //2.send connect app
    //3.recv result of connect and some others msg
    //4.send windows ack size
    //5.send set buffer length
    //6.send createStream
    //7._checkBw
    //8.recv result of createStream
    //9.send play
    //10.send set buffer length
    //11.recv result of play and some others msg
    //12.recv a/v data
    public void connect() throws IOException {
        socket = new Socket();
        SocketAddress socketAddress = new InetSocketAddress(mHost, mPort);
        long start = System.nanoTime();
        try {
            socket.connect(socketAddress, SOCKET_CONNECT_TIMEOUT_MS);
        } catch (SocketTimeoutException e) {
            Log.d(TAG, "tcp connect time out !!!");
            throw e;
        }
        socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
        mOutputStream = new BufferedOutputStream(socket.getOutputStream());
        mInputStream = new BufferedInputStream(socket.getInputStream());
        Log.d(TAG, "connect(): socket connection established, doing handhake... cost " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms");
        handshake(mInputStream, mOutputStream);
        sendRtmpPkt(createRtmpConnect());
        mState = State.CONNECT;
        while (mState != State.PLAY) {
            RtmpPacket rtmpPacket = recvRtmpPkt();
            handRtmpPkt(rtmpPacket);
            Log.d(TAG, "mState " + state2String(mState));
        }
        Log.d(TAG, "connect success, let's read av data");
    }

    public int readAvData(byte buf[], int size) throws IOException {
        int n = 0;
        RtmpPacket rtmpPacket = recvRtmpPkt();
        switch (rtmpPacket.getHeader().getMessageType()) {
            case DATA_AMF0: {
                //Log.d(TAG, " amf0 data");
                n = 0;
                Data metadata = (Data) rtmpPacket;
                if ("onMetaData".equals(metadata.getType()) /*&& offset >= 0*/) {
                    //make a flv header
                    //flv + version + flag(0x05 == audio + video) + header size (int 0x09) + prevtagsize(int 0)
                    byte[] flvheader = new byte[]{0x46, 0x4C, 0x56, 0x01, 0x05, 0x0, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00};
                    System.arraycopy(flvheader, 0, buf, 0, flvheader.length);
                    n += flvheader.length;

                    byte[] tag;
                    //write tag header
                    //type 1 byte
                    buf[n] = metadata.getHeader().getMessageType().getValue();
                    n += 1;

                    //data length 3 bytes
                    tag = Util.unsignedInt32ToByteArray(rtmpPacket.getHeader().getPacketLength());
                    System.arraycopy(tag, 1, buf, n, tag.length - 1);
                    n += (tag.length - 1);

                    //pts 3 bytes
                    tag = Util.unsignedInt32ToByteArray(metadata.getHeader().getAbsoluteTimestamp());
                    System.arraycopy(tag, 1, buf, n, tag.length - 1);
                    n += (tag.length - 1);

                    //reserved 4 bytes
                    System.arraycopy(RESERVED, 0, buf, n, RESERVED.length);
                    n += RESERVED.length;

                    //data
                    //convert rtmppacket to byte[]
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(rtmpPacket.getHeader().getPacketLength());
                    metadata.writeBody(baos);
                    //write tag data
                    System.arraycopy(baos.toByteArray(), 0, buf, n, baos.toByteArray().length);
                    n += baos.toByteArray().length;

                    //write prev tag size 4 bytes
                    tag = Util.unsignedInt32ToByteArray(baos.toByteArray().length + 11);
                    System.arraycopy(tag, 0, buf, n, tag.length);
                    n += tag.length;
                    Log.d(TAG, " onMetaData packet size " + n);
                }
                break;
            }
            case AUDIO:
            case VIDEO:
                //Log.d(TAG, " av/data");
                ContentData avdata = (ContentData) rtmpPacket;
                byte[] tag;
                //write tag header
                //type 1 byte
                buf[0] = avdata.getHeader().getMessageType().getValue();
                n += 1;

                //data length 3 bytes
                tag = Util.unsignedInt32ToByteArray(rtmpPacket.getHeader().getPacketLength());
                System.arraycopy(tag, 1, buf, n, tag.length - 1);
                n += (tag.length - 1);
                //Log.d(TAG, "packet len " + avdata.getHeader().getPacketLength() + " n " + n);

                //pts 3 bytes
                tag = Util.unsignedInt32ToByteArray(avdata.getHeader().getAbsoluteTimestamp());
                System.arraycopy(tag, 1, buf, n, tag.length - 1);
                n += (tag.length - 1);
                //Log.d(TAG, "pts " + avdata.getHeader().getAbsoluteTimestamp() + "n " + n);

                //reserved
                System.arraycopy(RESERVED, 0, buf, n, RESERVED.length);
                n += RESERVED.length;

                //data
                //Log.d(TAG, " avdata size " + avdata.getData().length);
                if (avdata.getData().length == 1) {
                    buf[n] = avdata.getData()[0];
                } else if (avdata.getData().length > buf.length - n) {
                    Log.d(TAG, " invalid  data length drop it ! pls read again");
                    return 0;
                } else {
                    System.arraycopy(avdata.getData(), 0, buf, n, avdata.getData().length);
                }
                n += avdata.getData().length;

                //write prev tag size
                tag = Util.unsignedInt32ToByteArray(avdata.getData().length + 11);
                System.arraycopy(tag, 0, buf, n, tag.length);
                n += tag.length;
                Log.d(TAG, " audio/video packet size " + n);
                break;
            default:
                n = 0;
                handRtmpPkt(rtmpPacket);
                //Log.w(TAG, "handleRxPacketLoop(): Not handling unimplemented/unknown packet of type: " + rtmpPacket.getHeader().getMessageType());
                break;
        }
        return n;
    }

    private String state2String(State state) {
        if (state == State.IDLE) {
            return "idle";
        } else if (state == State.HANDSHAKE) {
            return "handshake";
        } else if (state == State.CONNECT) {
            return "connect";
        } else if (state == State.CREATESTREAM) {
            return "createStream";
        } else if (state == State.PLAY) {
            return "play";
        }
        return null;
    }

    private void handshake(InputStream in, OutputStream out) throws IOException {
        Handshake handshake = new Handshake();
        handshake.writeC0(out);
        // Write C1 without waiting for S0
        handshake.writeC1(out);
        out.flush();
        handshake.readS0(in);
        handshake.readS1(in);
        handshake.writeC2(out);
        handshake.readS2(in);
        mState = State.HANDSHAKE;
    }

    private void handRtmpPkt(RtmpPacket rtmpPacket) throws IOException {
        switch (rtmpPacket.getHeader().getMessageType()) {
            case ABORT:
                mRtmpSessionInfo.getChunkStreamInfo(((Abort) rtmpPacket).getChunkStreamId()).clearStoredChunks();
                break;
            case USER_CONTROL_MESSAGE: {
                UserControl ping = (UserControl) rtmpPacket;
                switch (ping.getType()) {
                    case PING_REQUEST: {
                        ChunkStreamInfo channelInfo = mRtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.CONTROL_CHANNEL);
                        Log.d(TAG, "handleRxPacketLoop(): Sending PONG reply..");
                        UserControl pong = new UserControl(ping, channelInfo);
                        sendRtmpPkt(pong);
                        break;
                    }
                    case STREAM_EOF:
                        Log.d(TAG, "handleRxPacketLoop(): Stream EOF reached, closing RTMP writer...");
                        mOutputStream.close();
                        break;
                }
                break;
            }
            case WINDOW_ACKNOWLEDGEMENT_SIZE: {
                WindowAckSize windowAckSize = (WindowAckSize) rtmpPacket;
                Log.d(TAG, "handleRxPacketLoop(): Setting acknowledgement window size to: " + windowAckSize.getAcknowledgementWindowSize());
                mRtmpSessionInfo.setAcknowledgmentWindowSize(windowAckSize.getAcknowledgementWindowSize());
                sendRtmpPkt(createRtmpSendWindowAckSize());
                break;
            }
            case COMMAND_AMF0:
                handleRxInvoke((Command) rtmpPacket);
                break;
            case DATA_AMF0:
            case AUDIO:
            case VIDEO:
                break;
            default:
                Log.w(TAG, "handleRxPacketLoop(): Not handling unimplemented/unknown packet of type: " + rtmpPacket.getHeader().getMessageType());
                break;
        }
    }

    private void handleRxInvoke(Command invoke) throws IOException {
        String commandName = invoke.getCommandName();

        if (commandName.equals("_result")) {
            // This is the result of one of the methods invoked by us
            String method = mRtmpSessionInfo.takeInvokedCommand(invoke.getTransactionId());

            Log.d(TAG, "handleRxInvoke: Got result for invoked method: " + method);
            if ("connect".equals(method)) {
                // We can now send createStream commands
                sendRtmpPkt(createRtmpCreateStream());
                //let send checkbw packet
                sendRtmpPkt(createRtmpSendcheckBw());
                mState = State.CREATESTREAM;
            } else if ("createStream".contains(method)) {
                mCurrentStreamId = (int) ((AmfNumber) invoke.getData().get(1)).getValue();

                sendRtmpPkt(createRtmpSendgetStreamLength());
                sendRtmpPkt(createRtmpUserControl());
                sendRtmpPkt(createRtmpPlay());
                mState = State.PLAY;
            } else {
                Log.d(TAG, "handleRxInvoke(): '_result' message received for unknown method: " + method);
            }
        } else {
            Log.d(TAG, "handleRxInvoke(): Uknown/unhandled server invoke: " + invoke);
        }
    }

    private RtmpPacket recvRtmpPkt() throws IOException {
        RtmpPacket rtmpPacket;
        //insure read a complete rtmp packet
        try {
            while ((rtmpPacket = mRtmpDecoder.readPacket(mInputStream)) == null);
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "read rtmp packet timeout throw exception " + e);
            throw e;
        } catch (IOException e) {
            Log.e(TAG, "read catch unknow exception" + e);
            throw e;
        }

        return rtmpPacket;
    }

    private void sendRtmpPkt(RtmpPacket rtmpPacket) throws IOException {
        try {
            final ChunkStreamInfo chunkStreamInfo = mRtmpSessionInfo.getChunkStreamInfo(rtmpPacket.getHeader().getChunkStreamId());
            chunkStreamInfo.setPrevHeaderTx(rtmpPacket.getHeader());
            rtmpPacket.writeTo(mOutputStream, mRtmpSessionInfo.getChunkSize(), chunkStreamInfo);
            Log.d(TAG, "sendRtmpPkt wrote packet: " + rtmpPacket + ", size: " + rtmpPacket.getHeader().getPacketLength());
            if (rtmpPacket instanceof Command) {
                mRtmpSessionInfo.addInvokedCommand(((Command) rtmpPacket).getTransactionId(), ((Command) rtmpPacket).getCommandName());
            }
            mOutputStream.flush();
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "send rtmp packet timeout throw exception " + e);
            throw e;
        } catch (IOException e) {
            Log.e(TAG, "send catch unknow exception" + e);
            throw e;
        }
    }

    private RtmpPacket createRtmpConnect() {
        Command invoke = new Command("connect", ++transactionIdCounter, mRtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_COMMAND_CHANNEL));
        invoke.getHeader().setMessageStreamId(0);

        AmfObject args = new AmfObject();
        args.setProperty("app", mAppName);
        args.setProperty("flashVer", "LNX 9,0,124,2");
        args.setProperty("tcUrl", mTcUrl);
        args.setProperty("fpad", false);
        args.setProperty("capabilities", 15);
        args.setProperty("audioCodecs", 3191);
        args.setProperty("videoCodecs", 252);
        args.setProperty("videoFunction", 1);
        invoke.addData(args);
        invoke.getHeader().setAbsoluteTimestamp(0);
        return invoke;
    }

    private RtmpPacket createRtmpCreateStream() {
        final ChunkStreamInfo chunkStreamInfo = mRtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_COMMAND_CHANNEL);
        // Send createStream() command
        Command createStream = new Command("createStream", ++transactionIdCounter, chunkStreamInfo);
        createStream.getHeader().setAbsoluteTimestamp(0);
        createStream.getHeader().setTimestampDelta(0);
        return createStream;
    }

    private RtmpPacket createRtmpSendWindowAckSize() {
        final ChunkStreamInfo chunkStreamInfo = mRtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.CONTROL_CHANNEL);
        WindowAckSize was = new WindowAckSize(mRtmpSessionInfo.getAcknowledgementWindowSize(), chunkStreamInfo);
        was.setAcknowledgementWindowSize(mRtmpSessionInfo.getAcknowledgementWindowSize());
        return was;
    }

    // Send getStreamLength() command
    private RtmpPacket createRtmpSendgetStreamLength() {
        final ChunkStreamInfo chunkStreamInfo = mRtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.CONTROL_CHANNEL);
        Command getStreamLength = new Command("getStreamLength", ++transactionIdCounter, chunkStreamInfo);
        getStreamLength.getHeader().setAbsoluteTimestamp(0);
        getStreamLength.getHeader().setTimestampDelta(0);
        getStreamLength.addData(new AmfNull());
        getStreamLength.addData(mPlayPath);
        return getStreamLength;
    }

    // Send _checkbw() command
    private RtmpPacket createRtmpSendcheckBw() {
        final ChunkStreamInfo chunkStreamInfo = mRtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_COMMAND_CHANNEL);
        Command checkbw = new Command("_checkbw", ++transactionIdCounter, chunkStreamInfo);
        checkbw.getHeader().setAbsoluteTimestamp(0);
        checkbw.getHeader().setTimestampDelta(0);
        return checkbw;
    }

    private RtmpPacket createRtmpPlay() {
        Command play = new Command("play", ++transactionIdCounter);
        play.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_STREAM_CHANNEL);
        play.getHeader().setMessageStreamId(mCurrentStreamId);
        play.addData(new AmfNull());
        play.addData(mPlayPath); // what to play
        play.addData(-2000); // play duration
        return play;
    }

    private RtmpPacket createRtmpUserControl() {
        final ChunkStreamInfo chunkStreamInfo = mRtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_STREAM_CHANNEL);
        UserControl userControl = new UserControl(UserControl.Type.SET_BUFFER_LENGTH, chunkStreamInfo);
        userControl.getHeader().setChunkType(RtmpHeader.ChunkType.TYPE_1_RELATIVE_LARGE);
        userControl.getHeader().setAbsoluteTimestamp(0);
        userControl.getHeader().setTimestampDelta(0);
        userControl.setEventData(mCurrentStreamId, 3000);
        return userControl;
    }
}
