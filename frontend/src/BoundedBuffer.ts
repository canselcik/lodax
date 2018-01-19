export class BoundedBuffer {
    private _buffer: any = [];
    private _maxLen: number;
    constructor(maxLen: number) {
        this._maxLen = maxLen;
    }

    public getArray(): any {
        return this._buffer;
    }

    public setMaxLen(maxLen: number) {
        this._maxLen = maxLen;
    }

    public push(d: Date, val: any) {
        while (this._buffer.length > this._maxLen) {
            this._buffer.shift();
        } 
        this._buffer.push({x: d, y: val});
    }

    public shiftUntilGeq(first: Date) {
        var d = first.getTime();
        while (this._buffer.length > 0 && this._buffer[0]['x'].getTime() < d) {
            this._buffer.shift();
        }
    }

    public getLength(): number {
        return this._buffer.length;
    }

    public getDateAt(index: number): Date|null {
        if (index >= this._buffer.length)
            return null;
        return this._buffer[index]['x'];
    }

    public getAt(index: number): number|null {
        if (index >= this._buffer.length)
            return null;
        return this._buffer[index]['y'];
    }
}