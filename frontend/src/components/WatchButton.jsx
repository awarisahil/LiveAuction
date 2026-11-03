import { useState } from 'react';
import { api } from '../api';

export default function WatchButton({ auctionId, initial = false, onChange }) {
  const [watched, setWatched] = useState(initial);

  const toggle = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      if (watched) {
        await api.unwatch(auctionId);
        setWatched(false);
        onChange?.(false);
      } else {
        await api.watch(auctionId);
        setWatched(true);
        onChange?.(true);
      }
    } catch {
      // ignore; user will see auth errors elsewhere
    }
  };

  return (
    <button className={`watch-btn ${watched ? 'watched' : ''}`} onClick={toggle} title={watched ? 'Unwatch' : 'Watch'}>
      {watched ? '★' : '☆'}
    </button>
  );
}
