import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

let client = null;
let connecting = null;

export function getStompClient() {
  if (client?.connected) return Promise.resolve(client);
  if (connecting) return connecting;
  client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 2000,
  });
  connecting = new Promise((resolve, reject) => {
    client.onConnect = () => {
      connecting = null;
      resolve(client);
    };
    client.onStompError = (frame) => {
      connecting = null;
      reject(frame);
    };
    client.activate();
  });
  return connecting;
}

/** Subscribe to a STOMP topic; returns an unsubscribe function. */
export async function subscribe(topic, handler) {
  const c = await getStompClient();
  const sub = c.subscribe(topic, (message) => {
    try {
      handler(JSON.parse(message.body));
    } catch {
      // ignore malformed frame
    }
  });
  return () => sub.unsubscribe();
}
