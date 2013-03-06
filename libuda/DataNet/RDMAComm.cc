/*
** Copyright (C) 2012 Auburn University
** Copyright (C) 2012 Mellanox Technologies
** 
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at:
**  
** http://www.apache.org/licenses/LICENSE-2.0
** 
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
** either express or implied. See the License for the specific language 
** governing permissions and  limitations under the License.
**
**
*/

#include <stdio.h>
#include <sys/socket.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <malloc.h>
#include <netdb.h>
#include <errno.h>
#include <stdarg.h>

#include <infiniband/verbs.h>
#include <rdma/rdma_cma.h>

#include "RDMAComm.h"
#include "IOUtility.h"


int rdma_debug_flag = 0x0;

int
netlev_dealloc_conn_mem(netlev_mem_t *mem)
{
    if (ibv_dereg_mr(mem->mr)){
    	log(lsERROR,"ibv_dereg_mr failed (errno=%d)", errno);
    	return -1;
    }
    free(mem->wqe_start);
    free(mem->wqe_buff_start);
    free(mem);
    return 0;
}


int
netlev_dealloc_rdma_mem(struct netlev_dev *dev)
{
    if (ibv_dereg_mr(dev->rdma_mem->mr)){
    	log(lsERROR,"ibv_dereg_mr failed (errno=%d)", errno);
    	return -1;
    }
    free(dev->rdma_mem);
    return 0;
}

int
netlev_init_rdma_mem(void *mem, uint64_t total_size,
                     netlev_dev_t *dev)
{
    netlev_rdma_mem_t *rdma_mem;

    rdma_mem = (netlev_rdma_mem_t *) malloc(sizeof(netlev_rdma_mem_t));
    if (!rdma_mem) {
        output_stderr("[%s,%d] malloc struct netlev_rdma_mem failed",
                      __FILE__,__LINE__);
        return -1;
    }

    rdma_mem->total_size = total_size;
    rdma_mem->mr = ibv_reg_mr(dev->pd, mem, total_size, NETLEV_MEM_ACCESS_PERMISSION);
    if (!rdma_mem->mr) {
        log(lsERROR,"ibv_reg_mr failed for memory of total_size=%llu  , MSG=%m (errno=%d)", total_size, errno);
        free(rdma_mem);
        return -1;
    }

    dev->rdma_mem = rdma_mem;
    return 0;
}


int
netlev_init_conn_mem(struct netlev_conn *conn)
{
    netlev_mem_t *dev_mem;
    void         *wqe_mem;
    void         *dma_mem;

    int wqe_align = 64;
    int num_wqes  = wqes_perconn;
    int data_size = sizeof(netlev_msg_t) * num_wqes;
    int dma_align = getpagesize();

    log(lsDEBUG, "IDAN - wqes_perconn=%d", wqes_perconn );
    log(lsDEBUG, "SIGNAL_INTERVAL=%d", SIGNAL_INTERVAL);

    //alloc dev_mem struct
    dev_mem = (netlev_mem_t *) malloc(sizeof(netlev_mem_t));
    if (!dev_mem) {
        output_stderr("[%s,%d] malloc netlev_mem_t failed",
                      __FILE__,__LINE__);
        goto error_dev;
    }
    memset(dev_mem, 0, sizeof(struct netlev_mem));

    //alloc wqes
    wqe_mem = memalign(wqe_align, num_wqes * sizeof(netlev_wqe_t));
    if (!wqe_mem) {
        output_stderr("[%s,%d] malloc netlev_wqe_t failed",
                     __FILE__,__LINE__);
        goto error_wqe;
    }
    memset(wqe_mem, 0, num_wqes * sizeof (netlev_wqe_t));

    // alloc memory buffer for wqes
    dma_mem = memalign(dma_align, data_size);
    if (!dma_mem) {
        output_stderr("[%s,%d] malloc data buffer failed",
                     __FILE__,__LINE__);
        goto error_dma;
    }
    memset(dma_mem, 0, data_size);

    dev_mem->wqe_start = (netlev_wqe_t *)wqe_mem;
    dev_mem->wqe_buff_start = dma_mem;
    dev_mem->mr = ibv_reg_mr(conn->dev->pd, dma_mem, data_size, NETLEV_MEM_ACCESS_PERMISSION);
    if (!dev_mem->mr) {
    	log(lsFATAL, "register mem failed");
        goto error_register;
    }
    conn->mem = dev_mem;
    return 0;

error_register:
    free(dma_mem);
error_dma:
    free(wqe_mem);
error_wqe:
    free(dev_mem);
error_dev:
    return -1;
}






int
netlev_dev_release(struct netlev_dev *dev)
{
    if (ibv_destroy_cq(dev->cq)){
    	log(lsERROR,"ibv_destroy_cq failed (errno=%d)", errno);
    	return -1;
    }
    if (ibv_destroy_comp_channel(dev->cq_channel)){
		log(lsERROR,"ibv_destroy_comp_channel failed (errno=%d)", errno);
    	return -1;
	}

    if (netlev_dealloc_rdma_mem(dev)){
    	return -1;
    }

    if (ibv_dealloc_pd(dev->pd)){
   		log(lsERROR,"ibv_dealloc_pd failed (errno=%d)", errno);
    	return -1;
   	}
    return 0;
}


int 
netlev_dev_init(struct netlev_dev *dev)
{
    struct ibv_device_attr device_attr;
    int cqe_num, max_sge;

    memset(&device_attr, 0, sizeof(struct ibv_device_attr));

    dev->pd = ibv_alloc_pd(dev->ibv_ctx);
    if (!dev->pd) {
        output_stderr("[%s,%d] ibv_alloc_pd failed",
                      __FILE__,__LINE__);
        return -1;
    }

    if (ibv_query_device(dev->ibv_ctx, &device_attr) != 0) {
        output_stderr("[%s,%d] ibv_query_device",
                     __FILE__,__LINE__);
        return -1;
    }

    cqe_num = (CQ_SIZE> device_attr.max_cqe) ? device_attr.max_cqe : CQ_SIZE; //taking the minimum
    max_sge = device_attr.max_sge;

    dev->cq_channel = ibv_create_comp_channel(dev->ibv_ctx);
    if (!dev->cq_channel) {
        output_stderr("[%s,%d] ibv_create_comp_channel failed",
                      __FILE__,__LINE__);
        return -1;
    }

    dev->cq = ibv_create_cq(dev->ibv_ctx, cqe_num, NULL, dev->cq_channel, 0);
    if (!dev->cq) {
        output_stderr("[%s,%d] ibv_create_cq failed",
                      __FILE__,__LINE__);
        return -1;
    }
    log (lsDEBUG, "device_attr.max_cqe is %d, cqe_num is %d, actual cqe is %d, ", device_attr.max_cqe, cqe_num, dev->cq->cqe);

    if (ibv_req_notify_cq(dev->cq, 0) != 0) {
        output_stderr("[%s,%d] ibv_req_notify failed",
                     __FILE__,__LINE__);
        return -1;
    }

    log (lsDEBUG, "device_attr.max_sge is %d, device_attr.max_sge_rd is %d, ", device_attr.max_sge, device_attr.max_sge_rd);

    dev->max_sge = max_sge;
    dev->cqe_num = cqe_num;
	log(lsINFO, "Successfully init'ed device");
    return 0;
}
 
struct netlev_dev* 
netlev_dev_find(struct rdma_cm_id *cm_id, list_head_t *head)
{
    struct netlev_dev *dev = NULL;
    
    list_for_each_entry(dev, head, list) {
        if (dev->ibv_ctx == cm_id->verbs) {
            return dev;
        }
    }
    return NULL;
}

void 
netlev_conn_free(netlev_conn_t *conn)
{
    pthread_mutex_lock(&conn->lock);
    while (!list_empty(&conn->backlog)) {
    	netlev_msg_backlog_t *back = list_entry(conn->backlog.next, typeof(*back), list);
    	list_del(&back->list);
    	free(back->msg);
    	free(back);
    }
    pthread_mutex_unlock(&conn->lock);
    rdma_destroy_qp(conn->cm_id);
    if (rdma_destroy_id(conn->cm_id)){
        	log(lsERROR, "rdma_destroy_qp failed (errno=%d)", errno);
    }
    pthread_mutex_destroy(&conn->lock);
    netlev_dealloc_conn_mem(conn->mem);
    free(conn);
};

struct netlev_conn *
netlev_conn_alloc(netlev_dev_t *dev, struct rdma_cm_id *cm_id) 
{
    /* build a new connection structure */
    netlev_conn_t *conn;
    struct ibv_recv_wr *bad_wr;
    struct ibv_qp_init_attr qp_init_attr;
    struct ibv_qp_attr qp_attr;

    conn = (netlev_conn_t*) calloc(1, sizeof(netlev_conn_t));
    if (!conn) {
        log(lsERROR,"allocate conn failed");
        if (rdma_destroy_id(cm_id)){
        	log(lsERROR, "rdma_destroy_qp failed (errno=%d)", errno);
        }
        return NULL;
    }

    conn->cm_id = cm_id;
    conn->dev = dev;

    pthread_mutex_init(&conn->lock, NULL);
    INIT_LIST_HEAD(&conn->backlog);
    INIT_LIST_HEAD(&conn->list);

    if (netlev_init_conn_mem(conn) != 0) {
    	log(lsERROR, "failed to init connection");
        pthread_mutex_destroy(&conn->lock);
        if (rdma_destroy_id(cm_id)){
        	log(lsERROR, "rdma_destroy_qp failed (errno=%d)", errno);
        }
    	free(conn);
        return NULL;
     }

    memset(&qp_init_attr, 0, sizeof(qp_init_attr));
    qp_init_attr.send_cq  = dev->cq;
    qp_init_attr.recv_cq  = dev->cq;
    qp_init_attr.cap.max_send_wr  = wqes_perconn*2; //on server side 2 wqes are sent for each wqe received from client
    qp_init_attr.cap.max_recv_wr  = wqes_perconn;
    qp_init_attr.cap.max_send_sge = 16; /* 28 is the limit */
    qp_init_attr.cap.max_recv_sge = 16; /* 28 is the limit */
    qp_init_attr.cap.max_inline_data = sizeof(netlev_msg_t);
    qp_init_attr.qp_type = IBV_QPT_RC;
    qp_init_attr.sq_sig_all = 0;

    if (qp_init_attr.cap.max_recv_sge > dev->max_sge) {
        qp_init_attr.cap.max_recv_sge = dev->max_sge;
        qp_init_attr.cap.max_send_sge = dev->max_sge - 1;
    }

    if (rdma_create_qp(conn->cm_id, dev->pd, &qp_init_attr) != 0) {
        output_stderr("Create qp failed - %m");
        pthread_mutex_destroy(&conn->lock);
        netlev_dealloc_conn_mem(conn->mem);
        if (rdma_destroy_id(cm_id)){
        	log(lsERROR, "rdma_destroy_qp failed (errno=%d)", errno);
        }
        free(conn);
        return NULL;
    }

    conn->sent_counter = 0;
    conn->bad_conn = false;
    conn->received_counter = 0;
    conn->qp_hndl = conn->cm_id->qp;
    if (ibv_query_qp (conn->qp_hndl, &qp_attr, 12, &qp_init_attr)){
    	log(lsERROR,"ibv query failed - %m");
        netlev_conn_free(conn);
		return NULL;
    }
    log(lsTRACE,"actual inline size is %d", qp_attr.cap.max_inline_data);

    memset(&conn->peerinfo, 0, sizeof(connreq_data_t));
    log(lsDEBUG, "allocating %d wqes to be receving wqes", wqes_perconn);
    /* post as many recv wqes as possible, up to wqes_perconn */
    for (unsigned int i = 0; i < wqes_perconn; ++i) {
    	netlev_wqe_t *wqe = conn->mem->wqe_start + i;
    	wqe->data = (char *)(conn->mem->wqe_buff_start) + (i * sizeof(netlev_msg_t));
        init_wqe_recv(wqe, sizeof(netlev_msg_t), conn->mem->mr->lkey, conn);
        if (ibv_post_recv(conn->qp_hndl, &wqe->desc.rr, &bad_wr) != 0) {
            output_stderr("ibv_post_recv failed");
            netlev_conn_free(conn);
            return NULL;
        }
    }
    return conn;
};

struct netlev_conn* 
netlev_find_conn_by_qp (uint32_t qp_num, struct list_head *q)
{
    struct netlev_conn *conn = NULL;
    list_for_each_entry(conn, q, list) {
        if (conn->qp_hndl->qp_num == qp_num) {
            return conn;
        }
    }
    return NULL;
}

struct netlev_conn* 
netlev_find_conn_by_ip (unsigned long ipaddr, struct list_head *q)
{
    struct netlev_conn *conn = NULL;
    list_for_each_entry(conn, q, list) {
        if (conn->peerIPAddr == ipaddr) {
            return conn;
        }
    }
    return NULL;
}

void 
dprint(char *s, char *fmt, ...)
{
    char s1[256];
    va_list ap;
    va_start(ap, fmt);
    vsprintf(s1, fmt, ap);
    va_end(ap);
    fprintf(stderr, "%s %s", s, s1);
}




void
init_wqe_send (ibv_send_wr *send_wr,ibv_sge *sg, netlev_msg_t *h, unsigned int len,
               bool send_signal, void* context)
{
	send_wr->next       = NULL;
	if (send_signal){
		send_wr->send_flags = IBV_SEND_SIGNALED | IBV_SEND_INLINE;
	}else{
		send_wr->send_flags = IBV_SEND_INLINE;
	}
	send_wr->opcode     = IBV_WR_SEND;
	send_wr->wr_id      = (uintptr_t) context; //context (in server's case)is pointer to chunk that will be released after WC_SEND
	send_wr->num_sge    = 1;
	send_wr->sg_list    = sg;
    sg->length         = len;
	sg->addr           = (uintptr_t) h;
}

netlev_msg_backlog_t *
init_backlog_data(uint8_t type, uint32_t len, uint64_t src_req, void *context, char *msg)
{
	netlev_msg_backlog_t *back = (netlev_msg_backlog_t*)malloc (sizeof(netlev_msg_backlog_t));
    if (back == NULL) {
    	log(lsFATAL, "failed to allocate memory for netlev_msg_backlog");
    	exit(-1);
    }
	back->type = type;
	back->len = len;
	back->msg = (char*)malloc (sizeof(char)*len);
	memcpy(back->msg, msg, len);
	back->src_req = src_req;
	back->context = context;
	return back;
}


void 
init_wqe_recv (netlev_wqe_t *wqe, unsigned int len, 
               uint32_t lkey, netlev_conn_t *conn)
{
    wqe->desc.rr.next   = NULL;
    wqe->desc.rr.wr_id  = (uintptr_t) wqe;
    wqe->desc.rr.num_sge= 1;
    wqe->desc.rr.sg_list= &(wqe->sge);
    wqe->sge.length     = len;
    wqe->sge.lkey       = lkey;
    wqe->sge.addr       = (uintptr_t)(wqe->data);
    wqe->conn           = conn;
    wqe->type           = PTR_WQE;
//    wqe->state          = RECV_WQE_INIT;
}

netlev_wqe_t* 
get_netlev_wqe (struct list_head *head)
{
    netlev_wqe_t *wqe = NULL;

    if (list_empty(head)) return NULL;
        
    wqe = list_entry(head->next, typeof(*wqe), list);
    list_del(&wqe->list);
    return (wqe);
}


void
init_wqe_rdmaw(struct ibv_send_wr *send_wr, struct ibv_sge *sg, int len,
               void *laddr, uint32_t lkey,
               void *raddr, uint32_t rkey, struct ibv_send_wr *next_wr)
{
	send_wr->next       = next_wr;
	send_wr->opcode     = IBV_WR_RDMA_WRITE;
	send_wr->send_flags = 0; //setting to 0 to avoid getting completion event
//	send_wr->send_flags = IBV_SEND_SIGNALED;
	send_wr->wr_id      = (uintptr_t) send_wr;
	send_wr->num_sge    = 1;
	send_wr->sg_list    = sg;
    sg->length         = (len);
    sg->lkey           = (lkey);
    sg->addr           = (uintptr_t)(laddr);
    send_wr->wr.rdma.rkey = (rkey);
    send_wr->wr.rdma.remote_addr = (uintptr_t) (raddr);
}


int 
netlev_event_add(int poll_fd, int fd, int events, 
                 event_handler_t handler, void *data, 
                 struct list_head *head)
{
    progress_event_t *pevent;
    struct epoll_event ev;
    int err;

    memset(&ev, 0, sizeof(struct epoll_event));
    pevent = (progress_event_t *) calloc(1, sizeof(progress_event_t));
    pevent->fd = fd;
    pevent->data = data;
    pevent->handler = handler;

    ev.events = events;
    ev.data.fd = fd;
    ev.data.ptr = pevent;
    err = epoll_ctl(poll_fd, EPOLL_CTL_ADD, fd, &ev);
    log(lsTRACE, "EVENT adding handler=0x%x with data=0x%x; for: pollfd=%d; fd=%d", handler, data, poll_fd, fd);
    if (err) {
        log(lsERROR, "cannot add fd");
        return err;
    }

    list_add_tail(&pevent->list, head);
    return err;
}

void 
netlev_event_del(int poll_fd, int fd, struct list_head *head)
{
    progress_event_t *pevent;

    list_for_each_entry(pevent, head, list) {
        if (pevent->fd == fd) {
            log(lsTRACE, "EVENT deleting handler for: pollfd=%d; fd=%d", poll_fd, fd);
            epoll_ctl(poll_fd, EPOLL_CTL_DEL, fd, NULL);
            list_del(&pevent->list);
            free(pevent);
            return;
        }
    }
    
    log(lsERROR, "Event fd %d not found", fd);
}

struct netlev_conn*
netlev_init_conn(struct rdma_cm_event *event,
                 struct netlev_dev *dev)
{
    struct netlev_conn *conn = NULL;
    struct rdma_conn_param conn_param;
    struct connreq_data xdata;

    conn = netlev_conn_alloc(dev, event->id);
    if (!conn) {
        goto err_alloc_dev;
    }

    /* Save an extra one for credit flow */
    memset(&xdata, 0, sizeof(xdata));
    xdata.qp = conn->qp_hndl->qp_num;
    xdata.credits = wqes_perconn - 1;
    xdata.rdma_mem_rkey = dev->rdma_mem->mr->rkey;

    memset(&conn_param, 0, sizeof(conn_param));
    conn_param.responder_resources = 1;
    conn_param.initiator_depth = 1;
    conn_param.retry_count = RDMA_DEFAULT_RNR_RETRY;
    conn_param.rnr_retry_count = RDMA_DEFAULT_RNR_RETRY;
    conn_param.private_data = &xdata;
    conn_param.private_data_len = sizeof(xdata);

    /* accept the connection */
    if (rdma_accept(conn->cm_id, &conn_param) != 0) {
        output_stderr("[%s,%d] rdma_accept failed",
                      __FILE__,__LINE__);
        goto err_rdma_conn;
    }
    return conn;

err_rdma_conn:
    netlev_conn_free(conn);
err_alloc_dev:
    if (rdma_reject(event->id, NULL, 0) != 0) {
        output_stderr("[%s,%d] rdma_reject failed",
                      __FILE__,__LINE__);
    }
    return NULL;
}


struct netlev_conn*
netlev_conn_established(struct rdma_cm_event *event,
                        struct list_head *head)
{
    int found = 0;
    struct netlev_conn *conn;

    list_for_each_entry(conn, head, list) {
        if (conn->cm_id == event->id) {
            found = 1;
            break;
        }
    }

    if (!found) {
        output_stderr("event=%p id=%p qp_num=%d not found", 
                      event, event->id, event->id->qp->qp_num);
        return NULL;
    } else {
        conn->state = NETLEV_CONN_READY;
        output_stdout("A connection is fully ready conn=%p", conn);
        return conn;
    }
}


struct netlev_conn*
netlev_conn_find(struct rdma_cm_event *ev, struct list_head *head)
{
    struct netlev_conn *conn;

    list_for_each_entry(conn, head, list) {
        if (conn->qp_hndl->qp_num == ev->id->qp->qp_num) {
        	log(lsDEBUG, "conn was found based on ev->id->qp->qp_num=%x; ev->id->channel->fd=%d", (int)conn->qp_hndl->qp_num, ev->id->channel ? ev->id->channel->fd : 0);
            return conn;
        }
    }
	log(lsDEBUG, "conn was not found");
    return NULL;
}

void netlev_disconnect(struct netlev_conn *conn)
{
    if (conn) {
        rdma_disconnect(conn->cm_id);
        netlev_conn_free(conn);
    }
}

int
netlev_post_send(netlev_msg_t *h, int bytes,
                 uint64_t srcreq, void* context,
                 netlev_conn_t *conn, uint8_t msg_type)
{
	int rc;
    struct ibv_send_wr *bad_wr;

    struct ibv_send_wr   send_wr;
    ibv_sge sg ;

    if (conn->credits > 0 || msg_type == MSG_NOOP) {
    	//1 receiving wqe was set aside in order to send wqe if there are no credits

		int len = sizeof(netlev_msg_t)-(NETLEV_FETCH_REQSIZE-bytes);

		h->type = msg_type;
		h->tot_len = bytes;
		h->src_req = srcreq ? srcreq : 0;

		bool send_signal = !(conn->sent_counter%SIGNAL_INTERVAL);

		init_wqe_send(&send_wr, &sg, h, len, send_signal, context);
		log(lsTRACE,"signal is being sent %d", send_signal);

		pthread_mutex_lock(&conn->lock);
		h->credits = conn->returning;
		conn->returning = 0;
		conn->credits--;
		conn->sent_counter++;
		pthread_mutex_unlock(&conn->lock);
		rc = ibv_post_send(conn->qp_hndl, &send_wr, &bad_wr);
		if (rc) {
			log(lsERROR, "ibv_post_send error: errno=%d", rc);
			pthread_mutex_unlock(&conn->lock);
			return -1;
		}


		return 0;
    }else{
    		//there are no credits, save this to backlog
    	netlev_msg_backlog_t *back = init_backlog_data(msg_type, bytes, srcreq, context, h->msg);
    	list_add_tail(&back->list, &conn->backlog);
    	return -2;
    }
}


const char* netlev_stropcode(int opcode)
{
    switch (opcode) {
        case IBV_WC_SEND:
            return "IBV_WC_SEND";    
            break;

        case IBV_WC_RECV:
            return "IBV_WC_RECV";
            break;

    }
    return "NULL";
}

/*
 * Local variables:
 *  c-indent-level: 4
 *  c-basic-offset: 4
 * End:
 *
 * vim: ts=4 sw=4 hlsearch cindent expandtab 
 */
