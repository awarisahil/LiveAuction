import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import AuctionCard from '../components/AuctionCard';

const CATEGORIES = ['ELECTRONICS', 'FASHION', 'COLLECTIBLES', 'HOME', 'ART', 'SPORTS', 'VEHICLES', 'OTHER'];

export default function HomePage() {
  const { user } = useAuth();
  const [auctions, setAuctions] = useState([]);
  const [watching, setWatching] = useState(new Set());
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');

  useEffect(() => {
    let alive = true;
    const load = () =>
      api.auctions(q, category)
        .then((d) => alive && setAuctions(d.auctions))
        .catch(() => {});
    load();
    const timer = setInterval(load, 5000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [q, category]);

  useEffect(() => {
    if (!user) return;
    api.watchlist()
      .then((list) => setWatching(new Set(list.map((a) => a.id))))
      .catch(() => {});
  }, [user]);

  return (
    <main>
      <div className="toolbar">
        <input
          className="search"
          placeholder="Search auctions..."
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <div className="chips">
          <button className={`chip ${category === '' ? 'chip-active' : ''}`} onClick={() => setCategory('')}>
            All
          </button>
          {CATEGORIES.map((c) => (
            <button
              key={c}
              className={`chip ${category === c ? 'chip-active' : ''}`}
              onClick={() => setCategory(category === c ? '' : c)}
            >
              {c.toLowerCase()}
            </button>
          ))}
        </div>
      </div>
      <div className="grid">
        {auctions.map((a) => (
          <AuctionCard key={a.id} auction={a} watched={watching.has(a.id)} />
        ))}
        {auctions.length === 0 && <p className="empty">No live auctions match — try another search.</p>}
      </div>
    </main>
  );
}
