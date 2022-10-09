a = int(input())
for i in range(a):
    stroke = input().split(' = ')
    k = eval(stroke[0])
    b = {k : 'correct'}
    print(b.get(stroke[1], '\n'.join(['incorrect', stroke[0]])))
