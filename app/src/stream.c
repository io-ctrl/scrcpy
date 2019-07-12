#include "stream.h"

#include <libavformat/avformat.h>
#include <libavutil/time.h>
#include <SDL2/SDL_assert.h>
#include <SDL2/SDL_events.h>
#include <SDL2/SDL_mutex.h>
#include <SDL2/SDL_thread.h>
#include <unistd.h>

#include "compat.h"
#include "config.h"
#include "buffer_util.h"
#include "decoder.h"
#include "events.h"
#include "lock_util.h"
#include "log.h"
#include "recorder.h"

#define BUFSIZE 0x10000

#define HEADER_SIZE 12
#define NO_PTS UINT64_C(-1)

static bool
stream_recv_packet(struct stream *stream, AVPacket *packet) {
    // The video stream contains raw packets, without time information. When we
    // record, we retrieve the timestamps separately, from a "meta" header
    // added by the server before each raw packet.
    //
    // The "meta" header length is 12 bytes:
    // [. . . . . . . .|. . . .]. . . . . . . . . . . . . . . ...
    //  <-------------> <-----> <-----------------------------...
    //        PTS        packet        raw packet
    //                    size
    //
    // It is followed by <packet_size> bytes containing the packet/frame.

    uint8_t header[HEADER_SIZE];
    ssize_t r = net_recv_all(stream->socket, header, HEADER_SIZE);
    if (r < HEADER_SIZE) {
        return false;
    }

    uint64_t pts = buffer_read64be(header);
    uint32_t len = buffer_read32be(&header[8]);
    SDL_assert(len);

    void *buf = av_malloc(len);
    if (!buf) {
        LOGE("Could not allocate packet buffer");
        return false;
    }

    r = net_recv_all(stream->socket, buf, len);
    if (r < len) {
        av_free(buf);
        return false;
    }

    packet->data = buf;
    packet->size = len;
    packet->pts = pts != NO_PTS ? pts : AV_NOPTS_VALUE;

    return true;
}

static void
notify_stopped(void) {
    SDL_Event stop_event;
    stop_event.type = EVENT_STREAM_STOPPED;
    SDL_PushEvent(&stop_event);
}

static bool
process_config_packet(struct stream *stream, AVPacket *packet) {
    if (stream->recorder && !recorder_write(stream->recorder, packet)) {
        LOGE("Could not send config packet to recorder");
        return false;
    }
    return true;
}

static bool
process_frame(struct stream *stream, AVPacket *packet) {
    if (stream->decoder && !decoder_push(stream->decoder, packet)) {
        return false;
    }

    if (stream->recorder) {
        packet->dts = packet->pts;

        if (!recorder_write(stream->recorder, packet)) {
            LOGE("Could not write frame to output file");
            return false;
        }
    }

    return true;
}

static bool
stream_parse(struct stream *stream, const AVPacket *in_packet) {
    uint8_t *in_data = in_packet->data;
    int in_len = in_packet->size;
    uint8_t *out_data = NULL;
    int out_len = 0;
    while (in_len) {
        int r = av_parser_parse2(stream->parser, stream->codec_ctx,
                                 &out_data, &out_len, in_data, in_len,
                                 AV_NOPTS_VALUE, AV_NOPTS_VALUE, -1);
        in_data += r;
        in_len -= r;
        if (out_len) {
            AVPacket packet;
            av_init_packet(&packet);
            packet.data = out_data;
            packet.size = out_len;
            packet.pts = in_packet->pts;

            if (stream->parser->key_frame == 1) {
                packet.flags |= AV_PKT_FLAG_KEY;
            }

            bool ok = process_frame(stream, &packet);

            if (!ok) {
                LOGE("Could not process frame");
                return false;
            }
        }
    }

    return true;
}

static bool
stream_push_packet(struct stream *stream, AVPacket *packet) {
    AVPacket concat_packet;

    bool is_config = packet->pts == AV_NOPTS_VALUE;

    // A config packet must not be decoded immetiately (it contains no
    // frame); instead, it must be concatenated with the future data packet.
    if (stream->pending || is_config) {
        size_t new_size = stream->pending_size + packet->size;
        stream->pending = av_realloc(stream->pending, new_size);
        if (!stream->pending) {
            LOGE("Could not allocate buffer");
            return false;
        }

        memcpy(stream->pending + stream->pending_size,
               packet->data, packet->size);
        stream->pending_size = new_size;

        if (!is_config) {
            // prepare the concat packet to send to the decoder
            av_init_packet(&concat_packet);
            concat_packet.data = stream->pending;
            concat_packet.size = stream->pending_size;
            concat_packet.pts = packet->pts;
            concat_packet.dts = packet->dts;
            concat_packet.flags = packet->flags;
            packet = &concat_packet;
        }
    }

    if (is_config) {
        // config packet
        bool ok = process_config_packet(stream, packet);
        if (!ok) {
            return false;
        }
    } else {
        // data packet
        bool ok = stream_parse(stream, packet);
        if (!ok) {
            return false;
        }

        // pending has been consumed (or was already empty)
        av_free(stream->pending);
        stream->pending = NULL;
        stream->pending_size = 0;
    }
    return true;
}

static int
run_stream(void *data) {
    struct stream *stream = data;

    AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_H264);
    if (!codec) {
        LOGE("H.264 decoder not found");
        goto end;
    }

    stream->codec_ctx = avcodec_alloc_context3(codec);
    if (!stream->codec_ctx) {
        LOGC("Could not allocate codec context");
        goto end;
    }

    if (stream->decoder && !decoder_open(stream->decoder, codec)) {
        LOGE("Could not open decoder");
        goto finally_free_codec_ctx;
    }

    if (stream->recorder && !recorder_open(stream->recorder, codec)) {
        LOGE("Could not open recorder");
        goto finally_close_decoder;
    }

    stream->parser = av_parser_init(AV_CODEC_ID_H264);
    if (!stream->parser) {
        LOGE("Could not initialize parser");
        goto finally_close_recorder;
    }

    // We must only pass complete frames to av_parser_parse2()!
    // It's more complicated, but this allow to reduce the latency by 1 frame!
    stream->parser->flags |= PARSER_FLAG_COMPLETE_FRAMES;

    for (;;) {
        AVPacket packet;
        av_init_packet(&packet);
        bool ok = stream_recv_packet(stream, &packet);
        if (!ok) {
            // end of stream
            break;
        }

        ok = stream_push_packet(stream, &packet);
        if (!ok) {
            // cannot process packet (error already logged)
            av_free(packet.data);
            break;
        }

        av_free(packet.data);
    }

    LOGD("End of frames");

    av_free(stream->pending);

    av_parser_close(stream->parser);
finally_close_recorder:
    if (stream->recorder) {
        recorder_close(stream->recorder);
    }
finally_close_decoder:
    if (stream->decoder) {
        decoder_close(stream->decoder);
    }
finally_free_codec_ctx:
    avcodec_free_context(&stream->codec_ctx);
end:
    notify_stopped();
    return 0;
}

void
stream_init(struct stream *stream, socket_t socket,
            struct decoder *decoder, struct recorder *recorder) {
    stream->socket = socket;
    stream->decoder = decoder,
    stream->recorder = recorder;
    stream->pending = NULL;
    stream->pending_size = 0;
}

bool
stream_start(struct stream *stream) {
    LOGD("Starting stream thread");

    stream->thread = SDL_CreateThread(run_stream, "stream", stream);
    if (!stream->thread) {
        LOGC("Could not start stream thread");
        return false;
    }
    return true;
}

void
stream_stop(struct stream *stream) {
    if (stream->decoder) {
        decoder_interrupt(stream->decoder);
    }
}

void
stream_join(struct stream *stream) {
    SDL_WaitThread(stream->thread, NULL);
}
