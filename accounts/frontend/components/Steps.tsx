// 진행 단계 막대. current는 1부터 센다.
export default function Steps({ names, current }: { names: string[]; current: number }) {
  return (
    <ol aria-label="진행 단계" className="steps">
      {names.map((name, i) => (
        <li key={name} className={i < current ? "is-done" : undefined} aria-current={i + 1 === current ? "step" : undefined}>
          <div />
          <span>
            {i + 1}. {name}
          </span>
        </li>
      ))}
    </ol>
  );
}
