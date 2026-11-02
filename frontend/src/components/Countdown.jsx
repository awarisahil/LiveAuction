import { useEffect, useState } from 'react';

export default function Countdown({ endsAt, offset, ended }) {
  const [remain, setRemain] = useState(() => endsAt + offset - Date.now());

  useEffect(() => {
    const timer = setInterval(() => setRemain(endsAt + offset - Date.now()), 250);
    return () => clearInterval(timer);
  }, [endsAt, offset]);

  if (ended || remain <= 0) {
    return <p className="countdown ended">Auction ended</p>;
  }
  const totalSeconds = Math.floor(remain / 1000);
  const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const ss = String(totalSeconds % 60).padStart(2, '0');
  return <p className="countdown">Ends in {mm}:{ss}</p>;
}
