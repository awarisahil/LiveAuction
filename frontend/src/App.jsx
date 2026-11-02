import { useEffect, useState } from 'react';
import { listAuctions } from './api';
import { ToastProvider } from './toast';
import AuctionCard from './components/AuctionCard';
import AuctionDetail from './components/AuctionDetail';

function Shell() {
  const [bidder, setBidder] = useState(() => localStorage.getItem('bidder') || '');
  const [name, setName] = useState(bidder);
  const [selected, setSelected] = useState(null);
  const [auctions, setAuctions] = useState([]);

  useEffect(() => {
    let alive = true;
    const load = () =>
      listAuctions()
        .then((d) => alive && setAuctions(d.auctions))
        .catch(() => {});
    load();
    const timer = setInterval(load, 5000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  if (!bidder) {
    return (
      <div className="entry">
        <h1>Live Auctions</h1>
        <p className="muted">Pick a bidder name to join the floor.</p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const value = name.trim();
            if (!value) return;
            localStorage.setItem('bidder', value);
            setBidder(value);
          }}
        >
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Bidder name" autoFocus />
          <button type="submit">Enter</button>
        </form>
      </div>
    );
  }

  const back = () => {
    setSelected(null);
    listAuctions().then((d) => setAuctions(d.auctions)).catch(() => {});
  };

  return (
    <div className="app">
      <header>
        <h1 onClick={back}>Live Auctions</h1>
        <div className="who">
          Bidding as <strong>{bidder}</strong>{' '}
          <button
            className="link"
            onClick={() => {
              localStorage.removeItem('bidder');
              setBidder('');
              setName('');
            }}
          >
            switch
          </button>
        </div>
      </header>
      {selected ? (
        <AuctionDetail id={selected} bidder={bidder} onBack={back} />
      ) : (
        <main className="grid">
          {auctions.map((a) => (
            <AuctionCard key={a.id} auction={a} onOpen={() => setSelected(a.id)} />
          ))}
          {auctions.length === 0 && <p className="empty">No live auctions right now — check back soon.</p>}
        </main>
      )}
    </div>
  );
}

export default function App() {
  return (
    <ToastProvider>
      <Shell />
    </ToastProvider>
  );
}
